def call(Map args) {
    def svc = args.svc
    def selectedEnv = args.env

    // 🧭 Branch mapping
    def branchMap = [
        dev : 'CODAUIDEV',
        qa  : 'CODAUIQA',
        uat : 'CODAUIUAT',
        prod : 'CODAUIPROD'
    ]

    // 🏗️ Build command mapping
    def buildCmdMap = [
        dev : 'npm run build:dev',
        qa  : 'npm run build:qa',
        uat : 'npm run build:stg',
        prod : 'npm run build:prod'
    ]

    // 📄 YAML deployment files
    def yamlMap = [
        dev : "${svc}-dev-deployment.yml",
        qa  : "${svc}-qa-deployment.yml",
        uat : "${svc}-uat-deployment.yml",
        prod : "${svc}-prod-deployment.yml"
    ]

    // ☸️ Cluster mapping
    def clusterMap = [
        dev : 'DevFundCluster',
        qa  : 'DevFundCluster',
        uat : 'CODAUATCluster',
        prod : 'CODA-PROD-AKS-Cluster'
    ]

    // 🐳 Dockerfile mapping
    def dockerfileMap = [
        dev : 'Docker/Dev/Dockerfile',
        qa  : 'Docker/QA/Dockerfile',
        uat : 'Docker/UAT/Dockerfile',
        prod : 'Docker/PROD/Dockerfile'
    ]

    // storage account mapping
    def saMap = [
        dev : 'codadevsa',
        qa  : 'codadevsa',
        uat : 'codadevsa',
        prod : 'codaprodsa'
    ]

    // storage account key mapping
    def saKeySecretMap = [
        dev : 'CODADEVSA-PRIMARY-KEY',
        qa  : 'CODADEVSA-PRIMARY-KEY',
        uat : 'CODADEVSA-PRIMARY-KEY',
        prod : 'CODAPRODSA-PRIMARY-KEY'
    ]

	//resourceGroupMap mapping
    def resourceGroupMap = [
        dev : 'CODA_RG',
        qa  : 'CODA_RG',
        uat : 'CODA_RG',
        prod : 'CODA-PROD-RG'
    ]
	
    // 🔐 Key Vault mapping
    def keyVaultMap = [
        dev : 'CODADEV',
        qa  : 'CODADEV',
        uat : 'CODAUAT-KEYVAULT',
        prod : 'CODA-PROD-Azure-KeyVault'
    ]

    // 🌍 Common environment variables (same for all envs)
    def commonEnv = [
        USERNAME           : credentials('ACRUSERNAME'),
        PASSWD             : credentials('ACRPASSWD'),
        RESOURCE_GROUP     : 'CODA-PROD-RG',
        STORAGE_ACCOUNT    : 'codaprodsa',
        STORAGE_CONTAINER  : 'trivy-reports',
        STORAGE_KEY_SECRET : 'CODAPRODSA-ACCESSKEY'
    ]

    // Compute current values based on environment
    def currentBranch     = branchMap[selectedEnv]
    def currentBuildCmd   = buildCmdMap[selectedEnv]
    def currentYaml       = yamlMap[selectedEnv]
    def currentCluster    = clusterMap[selectedEnv]
    def currentDockerfile = dockerfileMap[selectedEnv]
    def currentKeyVault   = keyVaultMap[selectedEnv]
    def imageTag          = "${selectedEnv}-${env.BUILD_NUMBER}"
    def currentsa   = saMap[selectedEnv]
    def currentsaKeySecret   = saKeySecretMap[selectedEnv]
    def currentresourceGroup   = resourceGroupMap[selectedEnv]	

    echo """
    ============================================================
                    FRONTEND CONFIG DETAILS
    ------------------------------------------------------------
        🎨 Service Name     : ${svc}
        🌍 Environment      : ${selectedEnv}
        🌿 Git Branch       : ${currentBranch}
        ☸️ AKS Cluster      : ${currentCluster}
        📦 Dockerfile       : ${currentDockerfile}
        🐳 Docker Image Tag : ${imageTag}
        🔐 Key Vault        : ${currentKeyVault}

        --- Common Environment Variables ---
        👤 ACR Username       : [SECURE]
        💾 Storage Account    : ${commonEnv.STORAGE_ACCOUNT}
        📦 Container          : ${commonEnv.STORAGE_CONTAINER}
        🔑 Storage Key Secret : [SECURE]
    ============================================================
    """

    // Return all values together
    return [
        currentBranch     : currentBranch,
        currentBuildCmd   : currentBuildCmd,
        currentYaml       : currentYaml,
        currentCluster    : currentCluster,
        currentDockerfile : currentDockerfile,
        currentKeyVault   : currentKeyVault,
        imageTag          : imageTag,
		currentsa       : currentsa,
        currentsaKeySecret   : currentsaKeySecret,
        currentresourceGroup : currentresourceGroup		
    ] + commonEnv
}
 