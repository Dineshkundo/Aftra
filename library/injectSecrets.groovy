def call(Map args) {
    def currentKeyVault     = args.currentKeyVault
    def gradleFile          = args.gradleFile
    def gradleenvFile       = args.gradleenvFile
    def appPropsFile        = args.appPropsFile
    def appenvPropsFile     = args.appenvPropsFile

    echo "Logging into Azure..."
    sh 'az login --identity'

    echo "Fetching secrets from Azure Key Vault..."
    def clientId = sh(script: "az keyvault secret show --vault-name ${currentKeyVault} --name keyvault-client-id --query value -o tsv", returnStdout: true).trim()
    def clientSecret = sh(script: "az keyvault secret show --vault-name ${currentKeyVault} --name keyvault-client-secret --query value -o tsv", returnStdout: true).trim()
    def tenantId = sh(script: "az keyvault secret show --vault-name ${currentKeyVault} --name keyvault-tenant-id --query value -o tsv", returnStdout: true).trim()

    // mask secrets in console logs
    wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
        [password: clientId],
        [password: clientSecret],
        [password: tenantId]
    ]]) {
        echo "🛠️  Replacing placeholders in ${gradleFile},${gradleenvFile},${appenvPropsFile} and ${appPropsFile}..."

        sh """
            sed -i 's|KEYVAULT_CLIENT_ID|${clientId}|' ${gradleFile}
            sed -i 's|KEYVAULT_CLIENT_SECRET|${clientSecret}|' ${gradleFile}
            sed -i 's|KEYVAULT_TENANT_ID|${tenantId}|' ${gradleFile}

            sed -i 's|KEYVAULT_CLIENT_ID|${clientId}|' ${gradleenvFile}
            sed -i 's|KEYVAULT_CLIENT_SECRET|${clientSecret}|' ${gradleenvFile}
            sed -i 's|KEYVAULT_TENANT_ID|${tenantId}|' ${gradleenvFile}

            sed -i 's|KEYVAULT_CLIENT_ID|${clientId}|' ${appPropsFile}
            sed -i 's|KEYVAULT_CLIENT_SECRET|${clientSecret}|' ${appPropsFile}
            sed -i 's|KEYVAULT_TENANT_ID|${tenantId}|' ${appPropsFile}

            sed -i 's|KEYVAULT_CLIENT_ID|${clientId}|' ${appenvPropsFile}
            sed -i 's|KEYVAULT_CLIENT_SECRET|${clientSecret}|' ${appenvPropsFile}
            sed -i 's|KEYVAULT_TENANT_ID|${tenantId}|' ${appenvPropsFile}
        """
    }
}

