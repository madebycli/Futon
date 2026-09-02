// Repository refresh/error behavior adapted from Kototoro at dec0ef781644245f6937dc1cafc8ca84963fe08e.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.extensions.repo

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.db.dao.ExternalExtensionRepoDao
import io.github.landwarderer.futon.core.db.entity.ExternalExtensionRepoEntity
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalExtensionRepoRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: MangaDatabase,
    private val service: ExtensionRepoService,
) {

    private val dao: ExternalExtensionRepoDao
        get() = db.getExternalExtensionRepoDao()

    fun observeByType(type: ExternalExtensionType): Flow<List<ExternalExtensionRepo>> =
        dao.observeByType(type).map { list -> list.map { it.toDomain() } }

    suspend fun getByType(type: ExternalExtensionType): List<ExternalExtensionRepo> =
        dao.getByType(type).map { it.toDomain() }

    suspend fun addRepo(type: ExternalExtensionType, indexUrl: String): AddRepoResult =
        when (val prepared = prepareAddRepo(type, indexUrl)) {
            is PrepareAddRepoResult.Ready -> confirmAddRepo(prepared.repo)
            is PrepareAddRepoResult.DuplicateFingerprint -> AddRepoResult.DuplicateFingerprint(prepared.existingRepo)
            is PrepareAddRepoResult.FetchFailed -> AddRepoResult.FetchFailed(prepared.error)
            PrepareAddRepoResult.InvalidUrl -> AddRepoResult.InvalidUrl
            PrepareAddRepoResult.RepoAlreadyExists -> AddRepoResult.RepoAlreadyExists
        }

    suspend fun prepareAddRepo(type: ExternalExtensionType, indexUrl: String): PrepareAddRepoResult {
        Log.d(TAG, "prepareAddRepo:start type=$type input=$indexUrl")
        val normalizedIndexUrl = service.normalizeIndexUrl(indexUrl)
            ?: return PrepareAddRepoResult.InvalidUrl.also {
                Log.d(TAG, "prepareAddRepo:invalidUrl type=$type input=$indexUrl")
            }
        val baseUrl = service.baseUrlFromIndexUrl(normalizedIndexUrl)
        Log.d(TAG, "prepareAddRepo:normalized type=$type normalizedIndexUrl=$normalizedIndexUrl baseUrl=$baseUrl")
        if (dao.get(type, baseUrl) != null) return PrepareAddRepoResult.RepoAlreadyExists

        val repo = runCatching { service.fetchRepoDetails(baseUrl, type) }
            .onFailure { error ->
                Log.e(TAG, "prepareAddRepo:fetchFailed type=$type baseUrl=$baseUrl message=${error.message}", error)
            }
            .getOrElse { return PrepareAddRepoResult.FetchFailed(it) }

        // repo.json may canonicalize a legacy root URL to index_v2/index.pb.
        if (dao.get(type, repo.baseUrl) != null) {
            Log.d(TAG, "prepareAddRepo:duplicateResolvedBaseUrl type=$type baseUrl=${repo.baseUrl}")
            return PrepareAddRepoResult.RepoAlreadyExists
        }
        val duplicate = dao.getByFingerprint(type, repo.signingKeyFingerprint)
        if (duplicate != null) {
            return PrepareAddRepoResult.DuplicateFingerprint(duplicate.toDomain())
        }
        Log.d(TAG, "prepareAddRepo:ready type=$type baseUrl=${repo.baseUrl} name=${repo.displayName}")
        return PrepareAddRepoResult.Ready(repo)
    }

    suspend fun confirmAddRepo(repo: ExternalExtensionRepo): AddRepoResult {
        if (dao.get(repo.type, repo.baseUrl) != null) return AddRepoResult.RepoAlreadyExists
        val duplicate = dao.getByFingerprint(repo.type, repo.signingKeyFingerprint)
        if (duplicate != null) return AddRepoResult.DuplicateFingerprint(duplicate.toDomain())
        dao.upsert(repo.toEntity())
        Log.d(TAG, "confirmAddRepo:success type=${repo.type} baseUrl=${repo.baseUrl} name=${repo.displayName}")
        return AddRepoResult.Success(repo)
    }

    suspend fun delete(repo: ExternalExtensionRepo) {
        dao.delete(repo.type, repo.baseUrl)
    }

    suspend fun refresh(type: ExternalExtensionType) {
        getByType(type).forEach { refresh(it) }
    }

    suspend fun refresh(repo: ExternalExtensionRepo) {
        val refreshed = runCatching { service.fetchRepoDetails(repo.baseUrl, repo.type) }
        val now = System.currentTimeMillis()
        val entity = if (refreshed.isSuccess) {
            refreshed.getOrThrow().copy(
                createdAt = repo.createdAt,
                updatedAt = now,
                lastSuccessAt = now,
                lastError = null,
            ).toEntity()
        } else {
            val error = refreshed.exceptionOrNull()
            Log.e(TAG, "refresh:failed type=${repo.type} baseUrl=${repo.baseUrl} message=${error?.message}", error)
            repo.copy(
                updatedAt = now,
                lastError = error?.getDisplayMessage(appContext.resources) ?: "Unknown error",
            ).toEntity()
        }
        if (entity.baseUrl != repo.baseUrl) {
            dao.delete(repo.type, repo.baseUrl)
        }
        dao.upsert(entity)
    }

    suspend fun getAvailableExtensions(type: ExternalExtensionType): List<RepoAvailableExtension> =
        getCatalogExtensions(type).filter { it.isCompatible }

    suspend fun getCatalogExtensions(type: ExternalExtensionType): List<RepoAvailableExtension> = coroutineScope {
        Log.d(TAG, "getCatalogExtensions:start type=$type")
        getByType(type)
            .map { repo -> async { fetchCatalogExtensions(repo) } }
            .awaitAll()
            .flatten()
            .groupBy { it.pkgName }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.versionCode } }
            .sortedWith(compareBy<RepoAvailableExtension> { it.lang }.thenBy { it.name.lowercase() })
    }

    private suspend fun fetchCatalogExtensions(repo: ExternalExtensionRepo): List<RepoAvailableExtension> {
        return runCatching {
            service.fetchAvailableExtensionsOrThrow(repo)
        }.onSuccess {
            clearCatalogRefreshError(repo)
        }.onFailure { error ->
            Log.e(TAG, "catalog refresh failed type=${repo.type} baseUrl=${repo.baseUrl}", error)
            markCatalogRefreshFailed(repo, error)
        }.getOrDefault(emptyList())
    }

    private suspend fun clearCatalogRefreshError(repo: ExternalExtensionRepo) {
        if (repo.lastError == null) return
        dao.upsert(
            repo.copy(
                updatedAt = System.currentTimeMillis(),
                lastError = null,
            ).toEntity(),
        )
    }

    private suspend fun markCatalogRefreshFailed(repo: ExternalExtensionRepo, error: Throwable) {
        dao.upsert(
            repo.copy(
                updatedAt = System.currentTimeMillis(),
                lastError = error.getDisplayMessage(appContext.resources),
            ).toEntity(),
        )
    }

    sealed interface AddRepoResult {
        data class Success(val repo: ExternalExtensionRepo) : AddRepoResult
        data class DuplicateFingerprint(val existingRepo: ExternalExtensionRepo) : AddRepoResult
        data class FetchFailed(val error: Throwable) : AddRepoResult
        data object InvalidUrl : AddRepoResult
        data object RepoAlreadyExists : AddRepoResult
    }

    sealed interface PrepareAddRepoResult {
        data class Ready(val repo: ExternalExtensionRepo) : PrepareAddRepoResult
        data class DuplicateFingerprint(val existingRepo: ExternalExtensionRepo) : PrepareAddRepoResult
        data class FetchFailed(val error: Throwable) : PrepareAddRepoResult
        data object InvalidUrl : PrepareAddRepoResult
        data object RepoAlreadyExists : PrepareAddRepoResult
    }

    private companion object {
        const val TAG = "ExtensionRepo"
    }
}

private fun ExternalExtensionRepoEntity.toDomain(): ExternalExtensionRepo = ExternalExtensionRepo(
    type = type,
    baseUrl = baseUrl,
    name = name,
    shortName = shortName,
    website = website,
    signingKeyFingerprint = signingKeyFingerprint,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSuccessAt = lastSuccessAt,
    lastError = lastError,
    version = version,
)

private fun ExternalExtensionRepo.toEntity(): ExternalExtensionRepoEntity = ExternalExtensionRepoEntity(
    type = type,
    baseUrl = baseUrl,
    name = name,
    shortName = shortName,
    website = website,
    signingKeyFingerprint = signingKeyFingerprint,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSuccessAt = lastSuccessAt,
    lastError = lastError,
    version = version,
)
