def call(Map args) {
    def svc = args.svc
    def selectedEnv = args.env
 
    def branchMap = [
        dev : 'DEV',
        qa  : 'QA',
        uat : 'release/UAT',
        prod : 'release/Prod'
    ]
 
    def yamlMap = [
        dev : "${svc}-dev-deployment.yml",
        qa  : "${svc}-qa-deployment.yml",
        uat : "${svc}-uat-deployment.yml",
        prod : "${svc}-prod-deployment.yml"
    ]
 
    def clusterMap = [
        dev : 'DevFundCluster',
        qa  : 'DevFundCluster',
        uat : 'CODAUATCluster',
        prod : 'CODA-PROD-AKS-Cluster'
    ]
 
    def keyVaultMap = [
        dev : 'CODADEV',
        qa  : 'CODADEV',
        uat : 'CODAUAT-KEYVAULT',
        prod : 'CODA-PROD-Azure-KeyVault'
    ]
 
    def envMap = [
        dev : 'dev',
        qa  : 'qa',
        uat : 'uat',
        prod : 'prod'
    ]
 
    def saMap = [
        dev : 'codadevsa',
        qa  : 'codadevsa',
        uat : 'codadevsa',
        prod : 'codaprodsa'
    ]
 
    def saKeySecretMap = [
        dev : 'CODADEVSA-PRIMARY-KEY',
        qa  : 'CODADEVSA-PRIMARY-KEY',
        uat : 'CODADEVSA-PRIMARY-KEY',
        prod : 'CODAPRODSA-PRIMARY-KEY'
    ]
 
    def resourceGroupMap = [
        dev : 'CODA_RG',
        qa  : 'CODA_RG',
        uat : 'CODA_RG',
        prod : 'CODA-PROD-RG'
    ]
 
    // 🌍 Common environment variables (same for all envs)
    def commonEnv = [
        USERNAME           : credentials('ACRUSERNAME'),
        PASSWD             : credentials('ACRPASSWD'),
        // RESOURCE_GROUP     : 'CODA-PROD-RG',
        // STORAGE_ACCOUNT    : 'codaprodsa',
        STORAGE_CONTAINER  : 'trivy-reports',
        // STORAGE_KEY_SECRET : 'CODAPRODSA-ACCESSKEY'
    ]
 
    def currentBranch   = branchMap[selectedEnv]
    def currentYaml     = yamlMap[selectedEnv]
    def currentCluster  = clusterMap[selectedEnv]
    def currentKeyVault = keyVaultMap[selectedEnv]
    def suffix          = envMap[selectedEnv]
    def currentsa   = saMap[selectedEnv]
    def currentsaKeySecret   = saKeySecretMap[selectedEnv]
    def currentresourceGroup   = resourceGroupMap[selectedEnv]
    def gradleenvFile   = "gradle-${suffix}.properties"
    def appenvPropsFile = "src/main/resources/application-${suffix}.properties"
    def gradleFile      = "gradle.properties"
    def appPropsFile    = "src/main/resources/application.properties"
    def imageTag        = "${svc}-${selectedEnv}-${env.BUILD_NUMBER}"
 
    echo """
    ============================================================
                 BACKEND CONFIG DETAILS:
    ------------------------------------------------------------
            ✅ Service Name     : ${svc}
            🌍 Environment      : ${selectedEnv}
            🌿 Git Branch       : ${currentBranch}
            ☸️ AKS Cluster      : ${currentCluster}
            🔐 Key Vault        : ${currentKeyVault}
            📄 Gradle Properties: ${gradleenvFile},${gradleFile}
            📄 App Properties   : ${appPropsFile},${appenvPropsFile}
            🐳 Docker Image Tag : ${imageTag}

 
            --- Common Environment Variables ---
            👤 ACR Username       : [SECURE]
            💾 Storage Account    : ${saMap}
            📦 Container          : ${commonEnv.STORAGE_CONTAINER}
            🔑 Storage Key Secret : [SECURE]
    ============================================================
    """
 
    // return values to pipeline
    return [
        currentBranch   : currentBranch,
        currentYaml     : currentYaml,
        currentCluster  : currentCluster,
        currentKeyVault : currentKeyVault,
        gradleenvFile   : gradleenvFile,
        appenvPropsFile : appenvPropsFile,
        gradleFile      : gradleFile,
        appPropsFile    : appPropsFile,
        imageTag        : imageTag,
		currentsa       : currentsa,
        currentsaKeySecret   : currentsaKeySecret,
        currentresourceGroup : currentresourceGroup
    ] + commonEnv
}