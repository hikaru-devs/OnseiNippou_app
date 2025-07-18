# ベースイメージ
FROM eclipse-temurin:21-jre-alpine

# JARファイルをイメージ内にコピー
COPY target/OnseiNippou_app-0.0.1-SNAPSHOT.jar /app/app.jar

# ポート番号（例：8080）
EXPOSE 8080

# Spring Bootアプリの実行
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
