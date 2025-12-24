# PluginManagerTestPlugin (Paper 1.21.x)

This is a test Paper plugin that exercises all plugin-to-backend endpoints:

- `POST /plugin/register`
- `POST /plugin/heartbeat`
- `POST /plugin/players`
- `POST /plugin/console`
- `POST /plugin/status`

## Build

This repo includes a local Gradle distribution at:

`C:\Users\senpa\Documents\Gradle\gradle-8.14.3\bin\gradle.bat`

Build the plugin JAR:

`C:\Users\senpa\Documents\Gradle\gradle-8.14.3\bin\gradle.bat -p paper-test-plugin clean build`

Output:

`paper-test-plugin\build\libs\PluginManagerTestPlugin-1.0.0.jar`

## Install

Copy the JAR into your Paper server:

`plugins/PluginManagerTestPlugin-1.0.0.jar`

Start the server once so the config is generated:

`plugins/PluginManagerTestPlugin/config.yml`

Set backend URL:

`backendUrl: "http://localhost:3001"`

## Use

The plugin auto-registers on enable, then periodically sends:

- heartbeat
- players snapshot
- console lines

Manual testing command:

- `/pmtptest all`
- `/pmtptest register`
- `/pmtptest heartbeat`
- `/pmtptest players`
- `/pmtptest console`
- `/pmtptest status on`
- `/pmtptest status off`

