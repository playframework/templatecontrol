# templatecontrol

This is an internal script to update the Play managed example templates, to automate version management.

For the given templates in `application.conf`, it will:

* Clone the given template
* Create a new branch based off upstream version (i.e. upstream/2.5.x)
* Search and replace text to ensure that the relevant Play settings are correct, i.e. sbt-plugin, scalaVersion, sbt version, libraries etc.
* Git add and push the branch, and create a pull request against the upstream repo.

Because this is new, it is still up to a human to identify the pull request as passing the build hooks and merging it against the template.  

## Prerequisites

You will need to configure Github with a personal access token to get this working, because it does push branches into your personal fork of the templates in order to create the pull request.

You want a personal access token with "repo" scope.

https://help.github.com/articles/creating-an-access-token-for-command-line-use/

Then you need to create a script with your settings:

```
export TCONTROL_GITHUB_REMOTE=wsargent
export TCONTROL_GITHUB_USER=wsargent
export TCONTROL_GITHUB_OAUTH=<personal access token>
```

## Running

Once you've got the credentials, there's a single main method:

```
sbt run
```

## Debugging

The most common cause of bugs is that you already have a branch with that name -- you need to delete the branch after merging.

For more general problems, change the logback.xml file:

```
<logger name="templatecontrol" level="DEBUG"/>
```

## Copyright

Copyright Lightbend 2016

## License

This software is licensed under the Apache 2 license, quoted below.

Copyright (C) 2009-2016 Lightbend Inc. (https://www.lightbend.com).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
