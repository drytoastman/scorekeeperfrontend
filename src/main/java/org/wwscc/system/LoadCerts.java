package org.wwscc.system;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.swing.FocusManager;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileFilter;

import org.wwscc.system.docker.DockerAPI;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

import net.miginfocom.swing.MigLayout;

public class LoadCerts extends JFrame
{
    DockerAPI docker;
    JLabel status;
    boolean machineready;

    @SuppressWarnings("unchecked")
    public LoadCerts()
    {
        super("Load Certs");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel pane = new JPanel(new MigLayout("fill"));
        pane.setBackground(Color.WHITE);
        pane.setBorder(new LineBorder(Color.GRAY));

        status = new JLabel("", SwingConstants.CENTER);
        status.setFont(status.getFont().deriveFont(14f));
        pane.add(status, "grow");

        setContentPane(pane);
        setSize(250, 80);
        setVisible(true);

        docker = new DockerAPI();
        machineready = false;
        Messenger.register(MT.MACHINE_READY, (m, o) -> { machineready = (boolean)o; });
        Messenger.register(MT.DOCKER_ENV,    (m, o) -> docker.setup((Map<String,String>)o) );
    }

    void process() throws InterruptedException, IOException
    {
        MachineMonitor mmonitor = new MachineMonitor();
        mmonitor.start();

        JFileChooser fc = new JFileChooser(System.getProperty("user.home"));
        fc.setDialogTitle("Select the new certificates archive file");
        fc.setFileFilter(new CertsFileFilter());
        if (fc.showOpenDialog(FocusManager.getCurrentManager().getActiveWindow()) != JFileChooser.APPROVE_OPTION)
            return;

        status.setText("Waiting for VM");
        while (!machineready)
            Thread.sleep(300);

        status.setText("Waiting for Docker API");
        while (!docker.isReady())
            Thread.sleep(300);

        docker.loadVolume(ContainerMonitor.CERTS_VOL, fc.getSelectedFile().toPath(), s -> status.setText(s));
    }

    static class CertsFileFilter extends FileFilter {
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }
            for (String ext : new String[] { ".tgz", ".tar.gz", ".tar" }) {
                if (f.getName().endsWith(ext)) return true;
            }
            return false;
        }
        public String getDescription() {
            return "Certs Archive File (.tar, .tar.gz, .tgz)";
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException
    {
        AppSetup.appSetup("loadcerts");
        new LoadCerts().process();
        System.exit(0);
    }
}
