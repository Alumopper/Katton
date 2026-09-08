Copy `shared/` and `consumer/` into the test world's `kattonpacks/` on Fabric,
NeoForge or Paper. Start the server/world and check for `shared counter = 1`.

Edit only Consumer.kt, then run `/katton reload`: the counter should advance to 2,
because the dependency instance stays active. Edit Shared.kt and reload: both
instances should switch together and the counter should return to 1.

Add `error("fault injection")` to the consumer entrypoint and reload: the previous
consumer and shared binding must stay active. Remove the injected failure before
continuing. This probe does not undo arbitrary script side effects such as println.

To exercise ZIP transport, ZIP each directory's contents (manifest at ZIP root),
remove the copied directories, and restart. Do not leave duplicate manifest IDs.

For an automated Paper or Folia 26.1.2 smoke, first build `:paper:26.1.2:shadowJar`.
With `JAVA_HOME` pointing to Java 25 and Python 3 installed, run:

```sh
python3 examples/pack-dependencies/paper-smoke.py --server-jar /path/to/paper-or-folia.jar --eula-file /path/to/accepted/eula.txt
```

The EULA file must already contain `eula=true`. Optional `--plugin-jar` selects a
different plugin artifact; `--output` selects an empty directory for retained
logs and world files. `--vanilla-jar` supplies a predownloaded Mojang 26.1.2 JAR.
Add `--zip` to repeat all scenarios with ZIP packs, including hot reload of ZIPs.
The same command works on Windows with `python` and Windows paths.
It starts the built plugin in a temporary world bound to localhost on an ephemeral
port, checks dependency state, managed Bukkit listener rollback and delayed global
callbacks, then stops the
server. The temporary directory and log path are printed for inspection. It does
not modify an existing server world or its plugins. Global callback checks on
Folia do not establish entity-region or multi-region concurrency safety.
