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

For an automated Paper 26.1.2 smoke, first build `:paper:26.1.2:build` and prepare
the existing `paper/run/` server installation (including its accepted `eula.txt`).
With `JAVA_HOME` pointing to Java 25, run `python3 examples/pack-dependencies/paper-smoke.py`.
It starts the built plugin in a temporary world bound to localhost on an ephemeral
port, checks dependency state and managed Bukkit listener rollback, then stops the
server. The temporary directory and log path are printed for inspection. It does
not modify the existing Paper world or its plugins.
