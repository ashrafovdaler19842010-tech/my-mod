name: Build Mod

on: [push]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - run: unzip -o mod.zip || true
      
      - name: Configure Project
        run: |
          sed -i 's/^fabric_loader_version=.*/fabric_loader_version=0.16.9/' gradle.properties
          sed -i 's/^minecraft_version=.*/minecraft_version=1.21/' gradle.properties

          # УДАЛЯЕМ ВСЕ ЛИШНИЕ ФАЙЛЫ ИЗ ШАБЛОНА
          rm -f src/client/java/com/example/client/TemplateModClient.java
          rm -f src/client/java/com/example/client/SprintResetClient.java
          rm -f src/client/java/com/example/client/SprintResetScreen.java
          
          rm -f src/main/resources/*.mixins.json
          rm -f src/client/resources/*.mixins.json

          # СОЗДАЕМ fabric.mod.json
          cat << 'EOF' > src/main/resources/fabric.mod.json
          {
            "schemaVersion": 1,
            "id": "custommod",
            "version": "1.0.0",
            "name": "Custom Mod",
            "description": "Mod built from custom sources",
            "authors": ["You"],
            "contact": {},
            "license": "MIT",
            "environment": "client",
            "entrypoints": {
              "client": [
                "com.example.client.MainClient"
              ]
            },
            "depends": {
              "fabricloader": ">=0.16.0",
              "minecraft": "~1.21",
              "java": ">=21",
              "fabric-api": "*"
            }
          }
          EOF

          if ! grep -q "duplicatesStrategy" build.gradle; then
            echo "" >> build.gradle
            echo "tasks.named('jar') {" >> build.gradle
            echo "    duplicatesStrategy = DuplicatesStrategy.EXCLUDE" >> build.gradle
            echo "}" >> build.gradle
            echo "tasks.named('sourcesJar') {" >> build.gradle
            echo "    duplicatesStrategy = DuplicatesStrategy.EXCLUDE" >> build.gradle
            echo "}" >> build.gradle
          fi

          echo "tasks.withType(JavaCompile).configureEach { options.release = 21 }" >> build.gradle

      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          
      - run: chmod +x gradlew && ./gradlew clean build
      
      - uses: actions/upload-artifact@v4
        with:
          name: custom-mod
          path: | 
            build/libs/*.jar
            !build/libs/*-sources.jar
