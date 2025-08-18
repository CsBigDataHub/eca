# Features

## Chat

Chat is the main feature of ECA, allowing user to talk with LLM to behave like an agent, making changes using tools or just planning changes and next steps.

### Behaviors

![](./images/features/chat-behaviors.png)

Behavior affect the prompt passed to LLM and the tools to include, the current supported behaviors are:

- `plan`: Useful to plan changes and define better LLM plan before changing code via agent mode.
- `agent`: Make changes to code via file changing tools.

### Tools

![](./images/features/tools.png)

ECA leverage tools to give more power to the LLM, this is the best way to make LLMs have more context about your codebase and behave like an agent.
It supports both MCP server tools + ECA native tools.

### Native tools

ECA support built-in tools to avoid user extra installation and configuration, these tools are always included on models requests that support tools and can be [disabled/configured via config](./configuration.md) `nativeTools`.

#### Filesystem

Provides access to filesystem under workspace root, listing, reading and writing files, important for agentic operations.

- `eca_directory_tree`: list a directory as a tree (can be recursive).
- `eca_read_file`: read a file content.
- `eca_write_file`: write content to a new file.
- `eca_edit_file`: replace lines of a file with a new content.
- `eca_plan_edit_file`: Only used in plan mode, replace lines of a file with a new content.
- `eca_move_file`: move/rename a file.
- `eca_grep`: ripgrep/grep for paths with specified content.

#### Shell

Provides access to run shell commands, useful to run build tools, tests, and other common commands, supports exclude/include commands. 

- `eca_shell_command`: run shell command. Supports configs to exclude commands via `:nativeTools :shell :excludeCommands`.

#### Editor

Provides access to get information from editor workspaces.

- `eca_editor_diagnostics`: Ask client about the diagnostics (like LSP diagnostics).

### Contexts

![](./images/features/contexts.png)

User can include contexts to the chat (`@`), including MCP resources, which can help LLM generate output with better quality.
Here are the current supported contexts types:

- `file`: a file in the workspace, server will pass its content to LLM (Supports optional line range).
- `directory`: a directory in the workspace, server will read all file contexts and pass to LLM.
- `repoMap`: a summary view of workspaces files and folders, server will calculate this and pass to LLM. Currently, the repo-map includes only the file paths in git.
- `mcpResource`: resources provided by running MCPs servers.

#### AGENT.md automatic context

ECA will always include if found the `AGENT.md` file (configurable via `agentFileRelativePath` config) as context, searching for both `/project-root/AGENT.md` and `~/.config/eca/AGENT.md`.

You can ask ECA to create/update this file via `/init` command.

### Commands

![](./images/features/commands.png)

Eca supports commands that usually are triggered via shash (`/`) in the chat, completing in the chat will show the known commands which include ECA commands, MCP prompts and resources.

The built-in commands are:

`/init`: Create/update the AGENT.md file with details about the workspace for best LLM output quality.
`/costs`: Show costs about current session.
`/resume`: Resume a chat from previous session of this workspace folder.
`/doctor`: Show information about ECA, useful for troubleshooting.
`/repo-map-show`: Show the current repoMap context of the session.
`/prompt-show`: Show the final prompt sent to LLM with all contexts and ECA details.

#### Custom commands

It's possible to configure custom command prompts, for more details check [its configuration](./configuration.md#custom-commands)

##  Completion

Soon

## Edit 

Soon

