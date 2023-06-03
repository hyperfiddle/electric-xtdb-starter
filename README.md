# electric-xtdb-starter

* Adapted from [xtdb-in-a-box](https://github.com/xtdb/xtdb-in-a-box)
* Requires env var `XTDB_ENABLE_BYTEUTILS_SHA1=true`

```
$ XTDB_ENABLE_BYTEUTILS_SHA1=true clj -A:dev -X user/main

Starting Electric compiler and server...
shadow-cljs - server version: 2.20.1 running at http://localhost:9630
shadow-cljs - nREPL server started on port 9001
[:app] Configuring build.
[:app] Compiling ...
[:app] Build completed. (224 files, 0 compiled, 0 warnings, 1.93s)

👉 App server available at http://0.0.0.0:8080
```

## Using Calva?

If you are using Calva you can start the app like this instead:

0. Open the project in VS Code
1. Issue the command **Calva: Start a Project REPL and Connect (a.k.a Jack-in)

When "👉 App server available at http://0.0.0.0:8080" is printed in the Calva output/REPL window, you can control/cmd-click it and Calva will be connected both to the server and the client parts of the app.
