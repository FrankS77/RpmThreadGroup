package de.fschullerer.rpmthreads;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.gui.LoopControlPanel;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.gui.AbstractThreadGroupGui;

/**
 * JMeter Gui class.
 * Define labels and give explanations to properties.
 * There is only one property to set: "RPM list".
 */
public class RpmThreadGroupGui extends AbstractThreadGroupGui implements ItemListener {

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    private LoopControlPanel loopPanel;
    private JTextField rpmList;

    public RpmThreadGroupGui() {
        super();
        init();
        initGui();
    }

    protected final void init() {

        // RPM PROPERTIES
        VerticalPanel rpmPropsPanel = new VerticalPanel();
        rpmPropsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "Request per minute (RPM) properties"));

        // RPM list
        JPanel rpmPanel = new JPanel(new BorderLayout(5, 0));

        JLabel author = new JLabel("RPM Thread Group Jmeter plugin by  Frank Schullerer");
        author.setForeground(Color.blue);
        author.setFont(author.getFont().deriveFont(Font.PLAIN));


        JLabel help1 = new JLabel("Help: Insert a list of requests per minute and duration in the following " 
                                          + "syntax: startRPM1-endRPM1-duration1;startRPM2-endRPM2-duration2;" 
                                          + "startRPM3-endRPM3-duration3; ... ");
        help1.setForeground(Color.BLACK);
        help1.setFont(author.getFont().deriveFont(Font.PLAIN));

        JLabel help1a = new JLabel(
                "Attention: You can use more than one sampler in the RPM thread group but the RPM value refers " 
                        + "to only one sampler, which means that if there are more samplers, there will be more " 
                        + "requests in total since all samplers are always executed.");
        help1a.setForeground(Color.BLACK);
        help1a.setFont(author.getFont().deriveFont(Font.PLAIN));


        JLabel help2 = new JLabel("e.g.(1) 10-10-1 : constant 10 requests per minute for 1 minute. ");
        help2.setForeground(Color.BLACK);
        help2.setFont(author.getFont().deriveFont(Font.PLAIN));

        JLabel help3 = new JLabel("e.g. (2) 5-10-2;10-5-2  :  A increase of requests per minute from 5 to 10 "
                                          + "in 2 minutes and after that a decrease of requests per minute from 10 " 
                                          + "to 5 in 2 minutes");
        help3.setForeground(Color.BLACK);
        help3.setFont(author.getFont().deriveFont(Font.PLAIN));

        JLabel help4 = new JLabel(" e.g. (3) 2.5-20.5-8.5  :  Also float numbers are possible");
        help4.setForeground(Color.BLACK);
        help4.setFont(author.getFont().deriveFont(Font.PLAIN));

        rpmPropsPanel.add(author);
        rpmPropsPanel.add(help1);
        rpmPropsPanel.add(help1a);
        rpmPropsPanel.add(help2);
        rpmPropsPanel.add(help3);
        rpmPropsPanel.add(help4);
        JLabel rpmListLabel = new JLabel("RPM list");
        rpmPanel.add(rpmListLabel, BorderLayout.WEST);

        rpmList = new JTextField(5);
        rpmList.setName("RPM field");
        rpmListLabel.setLabelFor(rpmList);
        rpmPanel.add(rpmList, BorderLayout.CENTER);

        rpmPropsPanel.add(rpmPanel);

        VerticalPanel intgrationPanel = new VerticalPanel();
        intgrationPanel.add(rpmPropsPanel);

        add(intgrationPanel, BorderLayout.CENTER);
        // hide Loop panel
        createControllerPanel();
    }

    private JPanel createControllerPanel() {
        loopPanel = new LoopControlPanel(false);
        LoopController looper = (LoopController) loopPanel.createTestElement();
        looper.setLoops(-1);
        looper.setContinueForever(true);
        loopPanel.configure(looper);
        return loopPanel;
    }


    // Initialise the gui field values
    private void initGui() {
        rpmList.setText("10-10-1");
        loopPanel.clearGui();
    }

    @Override
    public String getLabelResource() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String getStaticLabel() {
        return "RPM Thread Group";
    }
    
    @Override
    public TestElement createTestElement() {
        RpmThreadGroup tg = new RpmThreadGroup();
        modifyTestElement(tg);
        return tg;
    }

    @Override
    public void modifyTestElement(TestElement tg) {
        super.configureTestElement(tg);
        if (tg instanceof RpmThreadGroup) {
            RpmThreadGroup utg = (RpmThreadGroup) tg;
            utg.setSamplerController((LoopController) loopPanel.createTestElement());
        }
        tg.setProperty(RpmThreadGroup.RPM_LIST, rpmList.getText());
    }

    @Override
    public void configure(TestElement tg) {
        super.configure(tg);
        TestElement te = (TestElement) tg.getProperty(AbstractThreadGroup.MAIN_CONTROLLER).getObjectValue();
        if (te != null) {
            loopPanel.configure(te);
        }


        rpmList.setText(tg.getPropertyAsString(RpmThreadGroup.RPM_LIST));
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        // TODO Auto-generated method stub

    }

}

