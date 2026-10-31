# When maven.fabricmc.net crawls

Some cloud containers get Maven Central 429s and only 2-40 KB/s per connection from maven.fabricmc.net, so a cold
Gradle cache never fills (mercury-0.4.3.jar alone is 11.8 MB). Parallel range requests scale, so:

    python3 tools/agent/slowmaven/mproxy.py > /tmp/mproxy.log 2>&1 &     # local caching proxy on 127.0.0.1:8765
    mkdir -p ~/.gradle/init.d && cp tools/agent/slowmaven/mirror.gradle ~/.gradle/init.d/
    printf 'systemProp.org.gradle.internal.http.socketTimeout=900000\nsystemProp.org.gradle.internal.http.connectionTimeout=120000\n' >> ~/.gradle/gradle.properties

The init script drops repo.maven.apache.org, puts Google's Maven Central mirror first and sends maven.fabricmc.net
and maven.kikugie.dev through the proxy, which fetches big files as 32 KB range requests 128 at a time. Nothing in
the repo's build changes. A cold cache took about 20 minutes (lane-d-1028-1009); stop the daemon (`./gradlew --stop`)
after adding the properties so they take effect.
