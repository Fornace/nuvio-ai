package com.nuvio.tv.core.media.provider.security

import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/** Connects provider credential cleanup to the app's existing profile lifecycle. */
@Singleton
class ProviderProfileCredentialStore @Inject constructor(
    private val vault: ProviderCredentialVault,
) : ProfileScopedCredentialStore {
    override suspend fun removeProfile(profileId: Int) {
        vault.deleteProfile(profileId)
    }

    override suspend fun clearAllProfiles() {
        vault.deleteAllProfiles()
    }
}
