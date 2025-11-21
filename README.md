# Java-Horde-Bridge

A Java based implementation for StableHorde's Generations UI. Aimed to be more fast and efficient, simple and easy to set-up.

## Features
- Text Generation Scribe Worker
  - KoboldAI Client Verifier
  - Generation Popper
- Interrogation Alchemist Worker
  - Image Captioning
  - NSFW Checking
  - Image Interrogation
  - Strip Background Post Processing

## How to install and use

Download the [latest release](https://github.com/LogicismDev/Java-Horde-Bridge/releases), extract the ZIP file, edit the configuration (config.yml), and use the provided .bat or .sh file to run the bridge worker.

Please note that this requires the usage of Java 11 or higher to use the bridge.

## Command Line Usage

| Argument Name | Argument Option | Description | Example Usage |
|--|--|--|--|
| API Key | -a, --kai_apikey | Set the Horde Worker API Key | -a Qjr8u2I0j2D8ZjXrA0aZ4r |
| Config File | -c, --config | The configuration file to grab the Horde Worker information from | -c config.yml |
| Interval | -i, --interval | The interval to check if there are new generations | -i 1 |
| Worker Type | -t, --worker_type | The Worker type to specify (text/interrogation) | -t text |
| KoboldAI URL | -k, --kai_url | The KoboldAI URL to grab generations from | -k http://127.0.0.1:5000 |
| Horde Worker Name | -n, --kai_name | Set the Horde Worker name | -n "A Java-Horde-Bridge Worker" |
| Priority Usernames | -p, --priority_usernames | The usernames to prioritize generations | -p Logicism#14426,db0#1,Henk#1326 |
| Cluster URL | -u, --cluster_url | Set the cluster url to grab prompts and to send generations to | -u https://aihorde.net |
| Backup Cluster URL | -b, --backup_cluster_url | Set the backup cluster url if the main cluster is down | -b https://stablehorde.net |
| Forms | -f, --forms | The interrogation forms to enable for the worker | -f caption,nsfw,interrogation,strip_background |