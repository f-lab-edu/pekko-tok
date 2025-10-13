# Pekko-Tok

## 로컬 실행

### 첫 번째 노드 실행 (클러스터 생성)

```shell
java -DPEKKO_HOSTNAME=127.0.0.1 \
     -DPEKKO_PORT=17355 \
     -Dserver.port=8080 \
     -jar pekko/build/libs/pekko.jar
```

### 두 번째 노드 실행 (클러스터 참여)

```shell
java -DPEKKO_HOSTNAME=127.0.0.1 \
     -DPEKKO_PORT=17356 \
     -Dserver.port=8081 \
     -jar pekko/build/libs/pekko.jar
```

### 세 번째 노드 실행 (클러스터 참여)

```shell
java -DPEKKO_HOSTNAME=127.0.0.1 \
     -DPEKKO_PORT=17357 \
     -Dserver.port=8082 \
     -jar pekko/build/libs/pekko.jar
```
