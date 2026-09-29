pipeline {
    agent any

    environment {
        VAULT_NAME    = 'CODADEV'
        REMOTE_HOST   = '10.0.9.38'
        REMOTE_USER   = 'adminuser'
        TOGGLE_SCRIPT = '/data-disk/scripts/toggle_cron.py'
    }

    parameters {
        choice(
            name: 'TARGET_JOB',
            choices: ['QA-Service', 'DEV-Service', 'AV-DEV', 'AV-QA'],
            description: 'Select the service cron job'
        )
        choice(
            name: 'ACTION',
            choices: ['COMMENT', 'UNCOMMENT'],
            description: 'COMMENT = disable | UNCOMMENT = enable'
        )
    }

    stages {
        stage('Toggle Cron') {
            steps {
                script {
                    def buildNum = env.BUILD_NUMBER
                    sh """
                        set -eu
                        set +x
                        umask 077

                        case "\$TARGET_JOB" in
                            "QA-Service")
                                MATCH_CMD="cd /home/matching-adminqa/CodaMatchingController/CodaMatchingService/ && ./CODAMatchingService"
                                ;;
                            "DEV-Service")
                                MATCH_CMD="cd /home/matching-adminqa/CodaMatchingControllerDEV/CodaMatchingService/ && ./CODAMatchingService"
                                ;;
                            "AV-DEV")
                                MATCH_CMD="cd /home/matching-adminqa/AVCodaMatchingControllerDEV/CodaMatchingService/ && ./CODAMatchingService"
                                ;;
                            "AV-QA")
                                MATCH_CMD="cd /home/matching-adminqa/AVCodaMatchingControllerQA/CodaMatchingService/ && ./CODAMatchingService"
                                ;;
                            *)
                                echo "ERROR: Invalid TARGET_JOB"
                                exit 1
                                ;;
                        esac

                        echo "===================================="
                        echo "Target job : \$TARGET_JOB"
                        echo "Action     : \$ACTION"
                        echo "Remote host: \$REMOTE_HOST"
                        echo "===================================="

                        WORK=\$(mktemp -d)
                        REMOTE_SCRIPT="/tmp/toggle_cron_${buildNum}.sh"
                        REMOTE_PY="/tmp/toggle_cron_${buildNum}.py"

                        trap 'rm -rf "\$WORK"' EXIT

                        # Fetch token
                        TOKEN=\$(curl --fail --silent --show-error \
                            --connect-timeout 10 --max-time 30 \
                            "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://vault.azure.net" \
                            -H "Metadata: true" | \
                            python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

                        # Fetch SSH key
                        curl --fail --silent --show-error \
                            --connect-timeout 10 --max-time 30 \
                            "https://\${VAULT_NAME}.vault.azure.net/secrets/Matching-Service-QA-Backup?api-version=7.4" \
                            -H "Authorization: Bearer \$TOKEN" | \
                            python3 -c "import sys,json; print(json.load(sys.stdin)['value'], end='')" \
                            > "\$WORK/ssh_key"

                        test -s "\$WORK/ssh_key"
                        chmod 600 "\$WORK/ssh_key"

                        # Build remote shell script
                        cat > "\$WORK/toggle_cron.sh" << 'REMOTE'
#!/bin/bash
set -euo pipefail
umask 077

MATCH_CMD="\$1"
ACTION="\$2"
CRONTAB="/usr/bin/crontab"
BACKUP=\$(mktemp)
NEW_CRON=\$(mktemp)

trap 'rm -f "\$BACKUP" "\$NEW_CRON"' EXIT

echo "Reading root crontab..."
sudo -n "\$CRONTAB" -l > "\$BACKUP"

echo "Running toggle logic..."
python3 "\$REMOTE_PY" "\$BACKUP" "\$NEW_CRON" "\$MATCH_CMD" "\$ACTION"

echo "Installing updated crontab..."
if ! sudo -n "\$CRONTAB" "\$NEW_CRON"; then
    echo "ERROR: Install failed — restoring backup"
    sudo -n "\$CRONTAB" "\$BACKUP"
    exit 1
fi

echo "===================================="
echo "Updated matching cron entry:"
echo "===================================="
sudo -n "\$CRONTAB" -l | grep -F -- "\$MATCH_CMD" || echo "No matching lines"
echo "SUCCESS: Root crontab updated."
REMOTE

                        chmod 700 "\$WORK/toggle_cron.sh"

                        # Inject REMOTE_PY path into remote script
                        sed -i "s|\\\$REMOTE_PY|\$REMOTE_PY|g" "\$WORK/toggle_cron.sh"

                        # Copy scripts to remote
                        scp -i "\$WORK/ssh_key" \
                            -o StrictHostKeyChecking=no \
                            -o BatchMode=yes \
                            -o ConnectTimeout=10 \
                            "\$TOGGLE_SCRIPT" \
                            "\$REMOTE_USER@\$REMOTE_HOST:\$REMOTE_PY"

                        scp -i "\$WORK/ssh_key" \
                            -o StrictHostKeyChecking=no \
                            -o BatchMode=yes \
                            -o ConnectTimeout=10 \
                            "\$WORK/toggle_cron.sh" \
                            "\$REMOTE_USER@\$REMOTE_HOST:\$REMOTE_SCRIPT"

                        # Execute
                        echo "Executing remote cron operation..."
                        ssh -i "\$WORK/ssh_key" \
                            -o StrictHostKeyChecking=no \
                            -o BatchMode=yes \
                            -o ConnectTimeout=10 \
                            "\$REMOTE_USER@\$REMOTE_HOST" \
                            "bash '\$REMOTE_SCRIPT' '\$MATCH_CMD' '\$ACTION'; rm -f '\$REMOTE_SCRIPT' '\$REMOTE_PY'"
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                def actionText = params.ACTION == 'COMMENT' ? 'disabled' : 'enabled'
                echo "✅ SUCCESS: ${params.TARGET_JOB} ${actionText} successfully."
            }
        }
        failure {
            echo "❌ FAILED: ${params.ACTION} for ${params.TARGET_JOB}. Check console output."
        }
        always {
            echo "Cron toggle pipeline finished."
        }
    }
}
