package templatecontrol

import templatecontrol.model.Play

object RunPlayAll {
  def main(args: Array[String]): Unit = {
    RunPlay26.main(args)
    RunPlay27.main(args)
  }
}

object RunPlay26 {
  def main(args: Array[String]): Unit =
    TemplateControl.runFor(Play.play26, args)
}

object RunPlay27 {
  def main(args: Array[String]): Unit =
    TemplateControl.runFor(Play.play27, args)
}
