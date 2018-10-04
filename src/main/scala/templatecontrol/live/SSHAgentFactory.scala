package templatecontrol.live

import com.jcraft.jsch.{JSch, JSchException, _}
import com.jcraft.jsch.agentproxy.{AgentProxyException, Connector, RemoteIdentityRepository}
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.util.FS

/**
 * Code to use ssh-agent when using JGit.
 */
class SSHAgentFactory extends JschConfigSessionFactory {

  override protected def configure(host: Host, session: Session): Unit = {
    session.setConfig("StrictHostKeyChecking", "false")
  }

  @throws[JSchException]
  override protected def createDefaultJSch(fs: FS): JSch = {
    var connector: Connector = null
    try
        if (SSHAgentConnector.isConnectorAvailable) {
          val usf = new JNAUSocketFactory
          connector = new SSHAgentConnector(usf)
        }
    catch {
      case e: AgentProxyException =>
        System.out.println(e)
    }

    import com.jcraft.jsch.JSch
    val jsch = super.createDefaultJSch(fs)
    if (connector != null) {
      JSch.setConfig("PreferredAuthentications", "publickey")
      val identityRepository = new RemoteIdentityRepository(connector)
      jsch.setIdentityRepository(identityRepository)
    }
    jsch
  }

}
