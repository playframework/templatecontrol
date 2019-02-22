# TemplateControl

This is an internal script to update the Play managed example templates, to automate version management.

For the given templates in `application.conf`, it will:

* Clone the given template
* Create a new branch based off upstream version (i.e. upstream/2.6.x)
* Search and replace text to ensure that the relevant Play settings are correct, i.e. sbt-plugin, scalaVersion, sbt version, libraries etc.
* Git add and push the branch, and create a pull request against the upstream repo.

Because this is new, it is still up to a human to identify the pull request as passing the build hooks and merging it against the template.

## Prerequisites

### Hub

You need to have Hub installed, see <https://hub.github.com/>.

### Project forks

You need to have forks for each of the repositories.

For that you can use the scripts `scripts/create-forks-play.sh` and `scripts/create-forks-lagom.sh`.

See [scripts/README.md](scripts/README.md) for more details.

### Personal Access Token

You will need to configure Github with a personal access token to get this working, because it does push branches into your personal fork of the templates in order to create the pull request.

You want a personal access token with "repo" scope.

<https://help.github.com/articles/creating-an-access-token-for-command-line-use/>

Then you need to create a script with your settings:

```
export TCONTROL_GITHUB_REMOTE=wsargent
export TCONTROL_GITHUB_USER=wsargent
export TCONTROL_GITHUB_OAUTH=<personal access token>
```

### Public Key for SSL access

Template control will use `https` when interacting with the upstream repos and `git` protocol when interacting with your own forks. When using `git` protocol it relies on your public ssh key as expected, but for unknown reasons, this will only work if your pub key was not generated with a passphrase.

### WebHook

As part of the whole process, the program will check the existence of a webhook to publish content in Lightbend TechHub. This will fail if you are not an owner on Playframework organisation in GitHub.

## Running

Once you've got the credentials, you can run it:

```
sbt run
```

or

```
sbt run --no-push
```

You will be presented if a few options to choose from. Choose the `main` method you would like to run:

```
Multiple main classes detected, select one to run:

 [1] templatecontrol.RunPlay
 [2] templatecontrol.RunPlay26
 [3] templatecontrol.RunPlay27

Enter number:
```


When using flag `--no-push`, no branch will be pushed and no PRs will be created on GitHub. You can then inspect the modified projects.

### Skipping lines

If you need to skip a line, you can add a `tc-skip` comment on the line you don't want to be replaced.
For example, in case you have a giter8 template with variables, you may want to leave them untouched.

The following line contains a giter8 variable, `$play_version$`,...
```
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "$play_version$")
```
this will be replaced by
```
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.19")
```
and that breaks the giter8 template.


If the line contains a `tc-skip`, it won't be touched, for example:
```
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "$play_version$") // tc-skip
```

## License

This software is licensed under the Apache 2 license, quoted below.

Copyright (C) 2016 Lightbend Inc. (https://www.lightbend.com).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
