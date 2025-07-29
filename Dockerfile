########## 1st stage : build ##########
FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# 1) Maven Wrapper をコピーして実行権限付与
COPY mvnw ./
RUN chmod +x mvnw

# 2) Maven のキャッシュ準備
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -B dependency:go-offline

# 3) ソースをコピーしてビルド
COPY src src
RUN ./mvnw -B clean package -DskipTests spring-boot:repackage

########## 2nd stage : runtime ##########
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 【ffmpeg を追加インストール】
RUN apk add --no-cache ffmpeg

# 【ビルド済みアプリJarをコピー】
COPY --from=builder /workspace/target/*SNAPSHOT.jar ./app.jar

ENTRYPOINT ["java","-jar","app.jar"]
