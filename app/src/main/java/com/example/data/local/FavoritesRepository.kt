package com.example.data.local

import kotlinx.coroutines.flow.Flow

class FavoritesRepository(private val dao: FavoriteActivityDao) {
  val favorites: Flow<List<FavoriteActivityEntity>> = dao.observeFavorites()

  suspend fun getTopFavorites(): List<FavoriteActivityEntity> = dao.getTopFavorites()

  suspend fun toggleFavorite(
    packageName: String,
    activityClassName: String?,
    customLabel: String,
    originalAppName: String,
    iconKey: String = "apps",
    accentColorArgb: Long = 0xFFE11D48L,
  ): Boolean {
    val existing = dao.findMatching(packageName, activityClassName)
    return if (existing != null) {
      dao.deleteById(existing.id)
      false
    } else {
      dao.insert(
        FavoriteActivityEntity(
          packageName = packageName,
          activityClassName = activityClassName,
          customLabel = customLabel,
          originalAppName = originalAppName,
          iconKey = iconKey,
          accentColorArgb = accentColorArgb,
        )
      )
      true
    }
  }

  suspend fun saveShortcut(
    editId: Long? = null,
    packageName: String,
    activityClassName: String?,
    customLabel: String,
    originalAppName: String,
    iconKey: String,
    accentColorArgb: Long,
    iconShape: String = "rounded",
    customIconPath: String? = null,
    iconZoom: Float = 1.0f,
    allowDuplicateForApp: Boolean = true,
  ): Long {
    if (editId != null && editId > 0L) {
      val updated = FavoriteActivityEntity(
        id = editId,
        packageName = packageName,
        activityClassName = activityClassName,
        customLabel = customLabel,
        originalAppName = originalAppName,
        iconKey = iconKey,
        accentColorArgb = accentColorArgb,
        iconShape = iconShape,
        customIconPath = customIconPath,
        iconZoom = iconZoom,
      )
      dao.update(updated)
      return editId
    }
    if (!allowDuplicateForApp) {
      val existing = dao.findMatching(packageName, activityClassName)
      if (existing != null) {
        dao.update(
          existing.copy(
            customLabel = customLabel,
            iconKey = iconKey,
            accentColorArgb = accentColorArgb,
            iconShape = iconShape,
            customIconPath = customIconPath,
            iconZoom = iconZoom,
          )
        )
        return existing.id
      }
    }
    return dao.insert(
      FavoriteActivityEntity(
        packageName = packageName,
        activityClassName = activityClassName,
        customLabel = customLabel,
        originalAppName = originalAppName,
        iconKey = iconKey,
        accentColorArgb = accentColorArgb,
        iconShape = iconShape,
        customIconPath = customIconPath,
        iconZoom = iconZoom,
      )
    )
  }

  suspend fun removeFavorite(id: Long) {
    dao.deleteById(id)
  }
}
