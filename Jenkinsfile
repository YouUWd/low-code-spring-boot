pipeline {
    // 在任何可用的代理上执行
    agent any
    tools {
        maven 'Maven-3.9.16' // 这个名字必须与全局工具配置里的一致
    }
    stages {
        stage('拉取代码') {
            steps {
                // 从配置的 Git 仓库拉取代码
                checkout scm
            }
        }

        stage('Maven 构建') {
            steps {
                // 使用 Maven 命令进行编译打包，并跳过测试
                sh 'mvn clean package -DskipTests'
            }
            post {
                // 构建成功后，归档生成的 jar 包，方便在 Jenkins 界面直接下载
                success {
                    archiveArtifacts artifacts: 'jdec-platform-app/target/*.jar', fingerprint: true
                }
            }
        }

        stage('部署到容器') {
            steps {
                script {
                    // 1. 定义关键路径变量
                    //    注意: 这里的 /app 是容器内路径，必须与你第一步中挂载的容器内路径一致
                    def containerName = 'jdec-setting'
                    def hostDeployDir = '/data/jdec-setting'
                    def containerDeployDir = '/app'


                    // 2. 查找最终的 jar 文件（防止因版本号变化而写死名称）
                    def jarFile = sh(script: "find jdec-platform-app/target -name '*.jar' -type f ! -name '*.original' | head -n 1", returnStdout: true).trim()
                    if (!jarFile) {
                        error "未找到 JAR 文件"
                    }
                    echo "找到 JAR 文件: ${jarFile}"

                    // 3. 停止容器
                    sh "docker stop ${containerName} || true"

                    // 4. 将 jar 包复制到宿主机部署目录
                    sh "cp ${jarFile} ${hostDeployDir}/app.jar"

                    // 4. 重启容器以应用新的 jar 包
                    //    此步骤会先停止并删除同名旧容器，然后用新的 jar 包启动新容器
                    sh "docker start ${containerName} || true"
                }
            }
        }
    }

    post {
        // 无论构建成功或失败，都清理工作空间，以节省磁盘空间
        always {
            cleanWs()
        }
    }
}