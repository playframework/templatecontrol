package templatecontrol

import templatecontrol.model.Lagom


object RunLagomAll {
  def main(args: Array[String]): Unit = {
    RunLagom14.main(args)
    RunLagom15.main(args)
  }
}

object RunLagom14 {
  def main(args: Array[String]): Unit =
    TemplateControl.runFor(Lagom.lagom14, args)
}

object RunLagom15 {
  def main(args: Array[String]): Unit =
    TemplateControl.runFor(Lagom.lagom15, args)
}
