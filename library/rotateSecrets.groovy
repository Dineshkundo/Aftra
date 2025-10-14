// vars/rotateSecrets.groovy
// Shared library entrypoint. Call: rotateSecrets(scope, envName)
def call(String scope, String envName = 'DEV') {
    echo "🔁 rotateSecrets called with scope='${scope}', env='${envName}'"

    // load params file that contains environments block
    def config = readYaml file: 'Key-Rotation/params.yaml'
    if (!config) {
        error "❌ Could not read Key-Rotation/params.yaml"
    }

    def envConfig = config.environments?.get(envName)
    if (!envConfig) {
        error "❌ Environment '${envName}' not found in Key-Rotation/params.yaml"
    }

    def keyVaultName = envConfig.keyVaultName
    if (!keyVaultName) {
        error "❌ keyVaultName missing for environment ${envName}"
    }

    echo "🔐 Using Key Vault: ${keyVaultName} for environment ${envName}"

    // Normalize scope tokens for simple comparisons
    def s = scope?.toLowerCase()

    if (s == 'storage-accounts' || s == 'both') {
        if (!envConfig.rotationTargets?.storageAccounts) {
            echo "ℹ️ No storageAccounts defined for ${envName}, skipping storage rotation."
        } else {
            rotateStorageKeys(envConfig.rotationTargets.storageAccounts, keyVaultName)
        }
    }

    if (s == 'app-registrations' || s == 'both') {
        if (!envConfig.rotationTargets?.appRegistrations) {
            echo "ℹ️ No appRegistrations defined for ${envName}, skipping app rotation."
        } else {
            rotateAppSecrets(envConfig.rotationTargets.appRegistrations, keyVaultName)
        }
    }

    echo "✅ rotateSecrets completed for scope='${scope}', env='${envName}'"
}

def rotateStorageKeys(storageAccounts, keyVaultName) {
    storageAccounts.each { secret ->
        def saName = secret.storageAccountName
        def keyType = secret.keyType ?: 'key1'
        def label = secret.name ?: "${saName}-${keyType}"

        echo "🔎 Processing storage account '${saName}' (key=${keyType}) -> KV secret '${label}'"

        // read current key in KeyVault (silent)
        def kvValue = sh(
            script: """az keyvault secret show --vault-name ${keyVaultName} --name ${label} --query value -o tsv || echo ''""",
            returnStdout: true
        ).trim()

        // read live key from storage account (silent)
        def liveKey = sh(
            script: """az storage account keys list --account-name ${saName} --query "[?keyName=='${keyType}'].value" -o tsv || echo ''""",
            returnStdout: true
        ).trim()

        if (!liveKey) {
            echo "⚠️ Could not fetch live key for ${saName}/${keyType}. Skipping."
            return
        }

        // If KV and liveKey are same, prompt to rotate; if different assume rotation already needed/done
        if (kvValue == liveKey) {
            // manual approval to rotate (optional)
            try {
                input message: "Rotate storage account key for ${label} (${saName} ${keyType})?", ok: "Rotate"
            } catch (e) {
                echo "✋ Rotation aborted by user for ${label}"
                return
            }

            echo "🔁 Rotating ${keyType} for storage account ${saName}..."
            sh """az storage account keys renew --account-name ${saName} --key ${keyType} --only-show-errors --output none"""
        } else {
            echo "⚠️ KeyVault value and live key differ for ${label} — skipping automatic rotate; proceeding to update Key Vault with current live key."
        }

        // Read the (current) live key again (must be present)
        def newKey = sh(
            script: """az storage account keys list --account-name ${saName} --query "[?keyName=='${keyType}'].value" -o tsv""",
            returnStdout: true
        ).trim()

        if (!newKey) {
            echo "❌ Failed to read new key for ${saName}/${keyType} — skipping KV update"
            return
        }

        // write to temp file and push to Key Vault (prevents logging secret)
        def tmpFile = ".tmp_${label}_${env.BUILD_ID}.secret"
        writeFile file: tmpFile, text: newKey

        sh """
            az keyvault secret set --vault-name ${keyVaultName} --name ${label} --file ${tmpFile} --only-show-errors --output none
            rm -f ${tmpFile}
        """
        echo "🔑 Updated KeyVault secret: ${label} (value hidden)"

        // Disable older KV versions for this secret
        disableOlderKeyVaultVersions(keyVaultName, label)
    }
}

def rotateAppSecrets(appRegistrations, keyVaultName) {
    appRegistrations.each { app ->
        def appName = app.name ?: app.appId
        def appId = app.appId
        def secretName = app.secretName ?: "${appName}-client-secret"
        def expiryDays = app.secretExpiryDays ?: 365

        echo "➡️ Rotating client secret for App Registration '${appName}' (appId=${appId}) -> KeyVault secret '${secretName}'"

        // Create new client secret and capture value (this operation requires proper AAD RBAC)
        def newSecret = sh(
            script: """az ad app credential reset --id ${appId} --append --years ${Math.max(1, Math.round(expiryDays/365))} --only-show-errors --query password -o tsv""",
            returnStdout: true
        ).trim()

        if (!newSecret) {
            error "❌ Failed to generate new client secret for appId ${appId}. Check permissions (Cloud Application Admin or app owner)."
        }

        // Write secret to temp file and push to Key Vault
        def tmpFile = ".tmp_${secretName}_${env.BUILD_ID}.secret"
        writeFile file: tmpFile, text: newSecret

        sh """
            az keyvault secret set --vault-name ${keyVaultName} --name ${secretName} --file ${tmpFile} --only-show-errors --output none
            rm -f ${tmpFile}
        """
        echo "🔑 Updated KeyVault secret: ${secretName} (value hidden)"

        // Disable older KV versions for this secret
        disableOlderKeyVaultVersions(keyVaultName, secretName)

        // NOTE: we DO NOT delete old AAD client secrets here — run cleanup pipeline separately after verification
    }
}

/**
 * Disable older (non-latest) Key Vault secret versions for a given secret.
 * Keeps the latest version enabled.
 */
def disableOlderKeyVaultVersions(String keyVaultName, String secretName) {
    echo "🧹 Disabling older Key Vault versions for ${secretName} in ${keyVaultName}"

    sh """
        # get full id of latest secret (or empty if not present)
        secretId=\$(az keyvault secret show --vault-name ${keyVaultName} --name ${secretName} --query id -o tsv 2>/dev/null || echo '')
        if [ -z "\$secretId" ]; then
            echo "⚠️ Secret '${secretName}' not found in ${keyVaultName}, skipping disable step."
            exit 0
        fi

        latestVer=\$(echo "\$secretId" | awk -F'/' '{print \$NF}')
        # list all versions (IDs), extract version segment
        az keyvault secret list-versions --vault-name ${keyVaultName} --name ${secretName} --query "[].id" -o tsv 2>/dev/null | awk -F'/' '{print \$NF}' | while read ver; do
            if [ "\$ver" != "\$latestVer" ]; then
                echo "🔒 Disabling old version: \$ver"
                az keyvault secret set-attributes --vault-name ${keyVaultName} --name ${secretName} --version \$ver --enabled false --only-show-errors --output none || true
            else
                echo "✅ Keeping latest version: \$ver"
            fi
        done
    """
}
