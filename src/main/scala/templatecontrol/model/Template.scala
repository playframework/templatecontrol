package templatecontrol.model


case class Project(name: String, branchName: String, templates: Seq[Template])
case class Template(name: String, githubOrg: String)