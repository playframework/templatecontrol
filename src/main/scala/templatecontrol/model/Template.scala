package templatecontrol.model

final case class Project(name: String, branchName: String, templates: Seq[Template])
final case class Template(name: String, githubOrg: String)
