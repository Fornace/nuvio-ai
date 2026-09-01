package com.nuvio.tv.core.profile

interface ProfileScopedCredentialStore {
    suspend fun removeProfile(profileId: Int)
    suspend fun clearAllProfiles()
}
