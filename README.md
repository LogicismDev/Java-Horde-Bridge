# Java-Horde-Bridge

A Java based implementation for StableHorde's Generations UI. Aimed to be more fast and efficient, simple and easy to set-up.

## Disclaimer

The Horde Bridge supports OpenAI Compatible Endpoints. This means any OpenAI Compatible AI Software (such as Ollama, LM Studio, etc.) can hook up with Horde with this program. If you do plan on using it on the horde, please note that I am not held responsible for any actions that may be held against your horde account (such as suspensions or bans, hosting an illegal or disallowed model loaded).

## Features

- Text Generation Scribe Worker
  - KoboldAI Client Verifier
  - OpenAI Compatible Endpoint Chat Completions Support
  - Generation Popper
- [Interrogation Alchemist Worker](https://github.com/LogicismDev/HordeInterrogateAgent)
  - Image Captioning
  - NSFW Checking
  - Image Interrogation
  - Strip Background Post Processing

## How to install and use

Download the [latest release](https://github.com/LogicismDev/Java-Horde-Bridge/releases), extract the ZIP file, edit the configuration (config.yml), and use the provided .bat or .sh file to run the bridge worker.

Please note that this requires the usage of Java 11 or higher to use the bridge.

## Command Line Usage

| Argument Name                               | Argument Option                      | Description                                                                                                                                                   | Example Usage                                                                   |  
|---------------------------------------------|--------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|  
| API Key                                     | -a, --kai_apikey                     | Set the Horde Worker API Key                                                                                                                                  | -a Qjr8u2I0j2D8ZjXrA0aZ4r                                                       |  
| Config File                                 | -c, --config                         | The configuration file to grab the Horde Worker information from                                                                                              | -c config.yml                                                                   |  
| Interval                                    | -i, --interval                       | The interval to check if there are new generations                                                                                                            | -i 1                                                                            |  
| Worker Type                                 | -t, --worker_type                    | The Worker type to specify (text/interrogation)                                                                                                               | -t text                                                                         |  
| KoboldAI URL                                | -k, --kai_url                        | The KoboldAI URL to grab generations from                                                                                                                     | -k http://127.0.0.1:5000                                                        |  
| Horde Worker Name                           | -n, --kai_name                       | Set the Horde Worker name                                                                                                                                     | -n "A Java-Horde-Bridge Worker"                                                 |  
| Priority Usernames                          | -p, --priority_usernames             | The usernames to prioritize generations                                                                                                                       | -p Logicism#14426,db0#1,Henk#1326                                               |  
| Cluster URL                                 | -u, --cluster_url                    | Set the cluster url to grab prompts and to send generations to                                                                                                | -u https://aihorde.net                                                          |  
| Backup Cluster URL                          | -b, --backup_cluster_url             | Set the backup cluster url if the main cluster is down                                                                                                        | -b https://stablehorde.net                                                      |  
| Forms                                       | -f, --forms                          | The interrogation forms to enable for the worker                                                                                                              | -f caption,nsfw,interrogation,strip_background                                  |
| Enable Prompt Jobs                          | -e, --enable_prompt_jobs             | Enable Prompt Jobs (true/false)                                                                                                                               | -e false                                                                        |
| Enable Chat Completions                     | -E, --enable_chat_completions        | Enable Chat Completions (true/false)                                                                                                                          | -E true                                                                         |
| Disable Kobold Client Verifier              | -d, --disable_kobold_client_verifier | Disable Kobold Client Verifier, only use if you're not using a KoboldAI supported endpoint program. Disables OpenAI model fetching if specified. (true/false) | -d true                                                                         |
| Model (OpenAI-Compatible Clients only)      | -o, --model                          | Set the model optionally if Kobold Client Verifier is disabled                                                                                                | -o YourModel/YourModelName                                                      |
| Max Tokens (OpenAI-Compatible Clients only) | -m, --max_tokens                     | Set the max tokens if Kobold Client Verifier is disabled                                                                                                      | -m 512                                                                          |
| Max Tokens (OpenAI-Compatible Clients only) | -M, --max_token_length               | Set the max tokens if Kobold Client Verifier is disabled                                                                                                      | -M 2048                                                                         |
| Chat Features                               | -C, --chat_features                  | The chat features to enable for the text worker                                                                                                               | -C seed,presence_penalty,frequency_penalty,vision,tools,prompt_overflow_refused |
| Image Transports                            | -I, --image_transports               | The image transports to enable for the text worker                                                                                                            | -I base64                                                                       | 