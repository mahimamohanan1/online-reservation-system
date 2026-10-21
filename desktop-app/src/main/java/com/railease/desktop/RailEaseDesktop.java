package com.railease.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public final class RailEaseDesktop {
    private static final Color NAVY = new Color(16, 26, 53);
    private static final Color BLUE = new Color(36, 87, 230);
    private static final Color ORANGE = new Color(255, 122, 48);
    private static final Color TEAL = new Color(20, 184, 166);
    private static final Color BACKGROUND = new Color(244, 247, 252);
    private static final Color INK = new Color(23, 32, 51);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JFrame frame = new JFrame("RailEase · Reservations");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private String username;
    private JTextField passenger, trainNumber, trainName, source, destination, pnrInput;
    private JComboBox<String> classType;
    private JSpinner journeyDate;
    private JLabel bookingMessage, cancellationMessage;
    private JPanel bookingDetails;
    private Database.Booking fetchedBooking;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { Database.initialize(); new RailEaseDesktop().show(); }
            catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Could not open the local SQLite database.\n" + e.getMessage(), "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void show() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 680)); frame.setSize(1040, 790);
        frame.setLocationRelativeTo(null); frame.setContentPane(root);
        root.add(loginPage(), "login"); cards.show(root, "login"); frame.setVisible(true);
    }

    private JPanel loginPage() {
        JPanel page = new JPanel(new GridBagLayout()); page.setBackground(BACKGROUND);
        JPanel split = new JPanel(new GridLayout(1, 2)); split.setPreferredSize(new Dimension(850, 520));
        JPanel intro = new JPanel(); intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS)); intro.setBackground(NAVY); intro.setBorder(new EmptyBorder(48, 42, 40, 42));
        JLabel logo = label("◉  railease", Color.WHITE, 26, true); intro.add(logo); intro.add(Box.createVerticalGlue());
        intro.add(label("YOUR NEXT CHAPTER", new Color(158, 181, 255), 12, true)); intro.add(Box.createVerticalStrut(16));
        intro.add(label("Every journey", Color.WHITE, 31, true)); intro.add(label("starts somewhere.", Color.WHITE, 31, true)); intro.add(Box.createVerticalStrut(14));
        intro.add(label("Reserve your seat and make the miles", new Color(203, 213, 232), 15, false));
        intro.add(label("between here and there count.", new Color(203, 213, 232), 15, false)); intro.add(Box.createVerticalStrut(30));
        TrainScene scene = new TrainScene();
        scene.setPreferredSize(new Dimension(340, 170)); scene.setMinimumSize(new Dimension(250, 150));
        scene.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190)); scene.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.add(scene); intro.add(Box.createVerticalStrut(16)); intro.add(Box.createVerticalGlue());
        intro.add(label("RAILEASE  ·  TRAVEL WITH EASE", new Color(150, 166, 197), 11, true));
        JPanel form = new JPanel(); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS)); form.setBackground(Color.WHITE); form.setBorder(new EmptyBorder(48, 46, 42, 46));
        form.add(label("Welcome aboard", INK, 29, true)); form.add(Box.createVerticalStrut(8)); form.add(label("Sign in to manage your journeys.", new Color(104, 117, 141), 14, false)); form.add(Box.createVerticalStrut(34));
        JTextField user = field("Username"); JPasswordField pass = passwordField("Password");
        form.add(fieldLabel("USERNAME")); form.add(Box.createVerticalStrut(7)); form.add(user); form.add(Box.createVerticalStrut(18));
        form.add(fieldLabel("PASSWORD")); form.add(Box.createVerticalStrut(7)); form.add(pass); form.add(Box.createVerticalStrut(24));
        JButton signIn = button("Sign in", BLUE); signIn.setAlignmentX(Component.LEFT_ALIGNMENT); signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48)); form.add(signIn); form.add(Box.createVerticalStrut(20));
        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); createRow.setBackground(Color.WHITE); createRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        createRow.add(label("New to RailEase?  ", new Color(104,117,141), 13, false)); JButton create = textButton("Create account"); createRow.add(create); form.add(createRow);
        Runnable login = () -> {
            String name = user.getText().trim(); char[] secret = pass.getPassword();
            if (name.isEmpty() || secret.length == 0) { message(form, "Enter your username and password.", true); return; }
            try {
                if (Database.login(name, secret)) { username = name; openDashboard(); }
                else message(form, "Invalid username or password.", true);
            } catch (Exception ex) { message(form, "Could not sign in: " + ex.getMessage(), true); }
            finally { java.util.Arrays.fill(secret, '\0'); }
        };
        signIn.addActionListener(e -> login.run()); pass.addActionListener(e -> login.run());
        create.addActionListener(e -> createAccount(user.getText().trim()));
        split.add(intro); split.add(form); page.add(split); return page;
    }

    private void createAccount(String suggested) {
        JTextField user = field(suggested.isBlank() ? "Choose a username" : suggested);
        JPasswordField pass = passwordField("At least 8 characters");
        JPasswordField confirm = passwordField("Re-enter password");
        JPanel p = new JPanel(new GridLayout(0,1,0,8)); p.add(new JLabel("Username")); p.add(user); p.add(new JLabel("Password")); p.add(pass); p.add(new JLabel("Confirm password")); p.add(confirm);
        int option = JOptionPane.showConfirmDialog(frame,p,"Create your RailEase account",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if(option!=JOptionPane.OK_OPTION)return;
        char[] a=pass.getPassword(), b=confirm.getPassword();
        try {
            if(user.getText().trim().isEmpty()||a.length<8)throw new IllegalArgumentException("Choose a username and a password with at least 8 characters.");
            if(!java.util.Arrays.equals(a,b))throw new IllegalArgumentException("The passwords do not match.");
            Database.register(user.getText().trim(),a);
            JOptionPane.showMessageDialog(frame,"Account created. You can now sign in.","Account ready",JOptionPane.INFORMATION_MESSAGE);
        } catch(Exception e){JOptionPane.showMessageDialog(frame,"Could not create account: "+e.getMessage(),"Account error",JOptionPane.ERROR_MESSAGE);}
        finally { java.util.Arrays.fill(a,'\0'); java.util.Arrays.fill(b,'\0'); }
    }

    private void openDashboard() {
        JPanel page = new JPanel(new BorderLayout()); page.setBackground(BACKGROUND);
        JPanel header = new JPanel(new BorderLayout()); header.setBackground(Color.WHITE); header.setBorder(new EmptyBorder(15,24,15,24));
        header.add(label("◉  railease", NAVY, 21, true), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0)); actions.setBackground(Color.WHITE);
        actions.add(label("Signed in as  " + username, new Color(86,101,129), 13, true)); JButton logout=textButton("Sign out"); actions.add(logout); logout.addActionListener(e->{username=null;root.add(loginPage(),"login");cards.show(root,"login");});
        header.add(actions,BorderLayout.EAST); page.add(header,BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane(); tabs.setFont(new Font("Segoe UI",Font.BOLD,14)); tabs.setBackground(Color.WHITE); tabs.addTab("  Book a journey  ",bookingPage()); tabs.addTab("  Manage booking  ",cancellationPage());
        JPanel center=new JPanel(new BorderLayout()); center.setBorder(new EmptyBorder(22,32,26,32)); center.setBackground(BACKGROUND);
        JPanel hero=new JPanel(); hero.setLayout(new BoxLayout(hero,BoxLayout.Y_AXIS)); hero.setBackground(NAVY); hero.setBorder(new EmptyBorder(25,30,24,30));
        hero.add(label("YOUR JOURNEY, MADE SIMPLE",new Color(166,187,255),11,true));hero.add(Box.createVerticalStrut(7));hero.add(label("Where to next?",Color.WHITE,27,true));hero.add(Box.createVerticalStrut(6));hero.add(label("Book a train ticket or find an existing reservation.",new Color(210,219,238),14,false));
        center.add(hero,BorderLayout.NORTH);center.add(tabs,BorderLayout.CENTER);page.add(center,BorderLayout.CENTER);
        root.add(page,"dashboard");cards.show(root,"dashboard");
    }

    private JPanel bookingPage() {
        JPanel outer=new JPanel(new BorderLayout(0,12));outer.setBackground(BACKGROUND);outer.setBorder(new EmptyBorder(20,2,0,2));
        JPanel card=new JPanel(new GridBagLayout());card.setBackground(Color.WHITE);card.setBorder(new EmptyBorder(22,26,22,26));GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(6,8,6,8);g.fill=GridBagConstraints.HORIZONTAL;g.weightx=1;
        g.gridx=0;g.gridy=0;g.gridwidth=2;card.add(label("Reserve your ticket",INK,21,true),g);g.gridy++;card.add(label("Fields marked with * are required. Train names come from your imported train list.",new Color(104,117,141),13,false),g);g.gridwidth=1;
        passenger=field("Passenger name");trainNumber=field("Numeric train number");trainName=field("Automatically populated");trainName.setEditable(false);trainName.setBackground(new Color(247,249,253));
        classType=new JComboBox<>(new String[]{"Choose class", "First AC", "Second AC", "Third AC", "Sleeper", "Chair Car", "Second Sitting"});styleCombo(classType);
        journeyDate=new JSpinner(new SpinnerDateModel(new Date(),new Date(),null,java.util.Calendar.DAY_OF_MONTH));journeyDate.setEditor(new JSpinner.DateEditor(journeyDate,"dd MMM yyyy"));styleInput(journeyDate.getEditor());
        source=field("Source station");destination=field("Destination station");
        addField(card,g,0,2,"Passenger name *",passenger);addField(card,g,1,2,"Train number *",trainNumber);addField(card,g,0,4,"Train name",trainName);addField(card,g,1,4,"Class type *",classType);addField(card,g,0,6,"Date of journey *",journeyDate);addField(card,g,1,6,"Source station *",source);addField(card,g,0,8,"Destination station *",destination);
        bookingMessage=new JLabel(" ");bookingMessage.setForeground(new Color(196,62,38));g.gridx=0;g.gridy=10;g.gridwidth=2;card.add(bookingMessage,g);
        JButton book=button("Book ticket",BLUE),importButton=button("Import train list (CSV)",NAVY);g.gridy=11;g.gridwidth=1;g.gridx=0;card.add(importButton,g);g.gridx=1;card.add(book,g);
        outer.add(card,BorderLayout.NORTH);
        trainNumber.getDocument().addDocumentListener((SimpleDocumentListener)()->lookupTrain());
        importButton.addActionListener(e->importTrains());book.addActionListener(e->bookTicket());
        return outer;
    }

    private void addField(JPanel panel,GridBagConstraints g,int column,int row,String title,JComponent input){g.gridx=column;g.gridy=row;g.gridwidth=1;panel.add(fieldLabel(title.toUpperCase()),g);g.gridy=row+1;panel.add(input,g);}

    private void lookupTrain(){if(trainNumber==null)return;String value=trainNumber.getText().trim();trainName.setText("");if(value.isEmpty()||!value.matches("\\d+"))return;try{String name=Database.trainName(Integer.parseInt(value));if(name!=null)trainName.setText(name);}catch(Exception ignored){}}

    private void importTrains(){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Select train list CSV (train_number,train_name)");if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;try{int n=Database.importTrains(chooser.getSelectedFile().toPath());lookupTrain();JOptionPane.showMessageDialog(frame,"Imported or updated "+n+" train records.","Train list ready",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage(),"Could not import train list",JOptionPane.ERROR_MESSAGE);}}

    private void bookTicket(){
        String number=trainNumber.getText().trim(), from=source.getText().trim(),to=destination.getText().trim();LocalDate date=((Date)journeyDate.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if(passenger.getText().trim().isEmpty()||number.isEmpty()||classType.getSelectedIndex()==0||from.isEmpty()||to.isEmpty()){bookingMessage.setText("Complete all required fields before booking.");return;}
        if(!number.matches("\\d+")){bookingMessage.setText("Train number must contain digits only.");return;}
        if(trainName.getText().isBlank()){bookingMessage.setText("Train number is not in the database. Import a valid train list.");return;}
        if(date.isBefore(LocalDate.now())){bookingMessage.setText("Journey date cannot be in the past.");return;}
        if(from.equalsIgnoreCase(to)){bookingMessage.setText("Source and destination must be different.");return;}
        try{
            Database.Booking input=new Database.Booking(passenger.getText().trim(),Integer.parseInt(number),trainName.getText(),(String)classType.getSelectedItem(),date,from,to);
            String pnr=Database.book(input);Database.Booking saved=Database.find(pnr);bookingMessage.setText(" ");showConfirmation(saved);
        }catch(Exception ex){bookingMessage.setText(ex.getMessage());}
    }

    private void showConfirmation(Database.Booking b){String text="Ticket confirmed!\n\nPassenger: "+b.passengerName()+"\nPNR: "+b.pnr()+"\nTrain: "+b.trainNumber()+" · "+b.trainName()+"\nClass: "+b.classType()+"\nDate: "+b.journeyDate().format(DATE)+"\nRoute: "+b.source()+"  →  "+b.destination();JOptionPane.showMessageDialog(frame,text,"Your ticket is confirmed",JOptionPane.INFORMATION_MESSAGE);}

    private JPanel cancellationPage(){
        JPanel outer=new JPanel(new BorderLayout(0,16));outer.setBackground(BACKGROUND);outer.setBorder(new EmptyBorder(20,20,20,20));
        JPanel find=new JPanel(new GridBagLayout());find.setBackground(Color.WHITE);find.setBorder(new EmptyBorder(22,25,22,25));GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(6,7,6,7);g.fill=GridBagConstraints.HORIZONTAL;g.weightx=1;
        g.gridx=0;g.gridy=0;g.gridwidth=2;find.add(label("Find a reservation",INK,20,true),g);g.gridy++;find.add(fieldLabel("PNR NUMBER"),g);pnrInput=field("Enter your PNR");g.gridy++;g.gridwidth=1;g.gridx=0;find.add(pnrInput,g);JButton fetch=button("Fetch booking",BLUE);g.gridx=1;g.weightx=0;find.add(fetch,g);
        cancellationMessage=new JLabel(" ");cancellationMessage.setForeground(new Color(196,62,38));g.gridx=0;g.gridy++;g.gridwidth=2;find.add(cancellationMessage,g);outer.add(find,BorderLayout.NORTH);
        bookingDetails=new JPanel(new BorderLayout(0,12));bookingDetails.setBackground(Color.WHITE);bookingDetails.setBorder(new EmptyBorder(20,25,22,25));bookingDetails.add(label("Enter a PNR above to view a reservation.",new Color(104,117,141),14,false),BorderLayout.CENTER);outer.add(bookingDetails,BorderLayout.CENTER);
        fetch.addActionListener(e->fetchBooking());return outer;
    }

    private void fetchBooking(){String pnr=pnrInput.getText().trim();if(pnr.isEmpty()){cancellationMessage.setText("Enter a PNR to search.");return;}try{fetchedBooking=Database.find(pnr);if(fetchedBooking==null){cancellationMessage.setText("No active reservation was found for that PNR.");renderBooking(null);return;}cancellationMessage.setText(" ");renderBooking(fetchedBooking);}catch(Exception e){cancellationMessage.setText("Could not retrieve booking: "+e.getMessage());}}

    private void renderBooking(Database.Booking b){bookingDetails.removeAll();if(b==null){bookingDetails.add(label("No booking details to display.",new Color(104,117,141),14,false),BorderLayout.CENTER);bookingDetails.revalidate();bookingDetails.repaint();return;}
        JPanel info=new JPanel(new GridLayout(0,2,15,13));info.setBackground(Color.WHITE);addInfo(info,"PNR",b.pnr());addInfo(info,"Passenger",b.passengerName());addInfo(info,"Train",b.trainNumber()+" · "+b.trainName());addInfo(info,"Class",b.classType());addInfo(info,"Journey date",b.journeyDate().format(DATE));addInfo(info,"From",b.source());addInfo(info,"To",b.destination());bookingDetails.add(info,BorderLayout.CENTER);
        JPanel route=new JPanel(new FlowLayout(FlowLayout.CENTER,18,8));route.setBackground(new Color(244,247,252));route.add(label(b.source(),NAVY,14,true));route.add(label("● ━━━━━━━━━━━━━ ●",BLUE,14,true));route.add(label(b.destination(),NAVY,14,true));bookingDetails.add(route,BorderLayout.NORTH);
        JButton cancel=button("Confirm cancellation",new Color(196,62,38));JPanel bottom=new JPanel(new FlowLayout(FlowLayout.RIGHT));bottom.setBackground(Color.WHITE);bottom.add(cancel);bookingDetails.add(bottom,BorderLayout.SOUTH);
        cancel.addActionListener(e->{int answer=JOptionPane.showConfirmDialog(frame,"Are you sure you want to cancel reservation "+b.pnr()+"? This will remove it from the database.","Confirm cancellation",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);if(answer==JOptionPane.YES_OPTION)try{if(Database.cancel(b.pnr())){fetchedBooking=null;renderBooking(null);cancellationMessage.setForeground(TEAL);cancellationMessage.setText("Reservation cancelled successfully.");}else cancellationMessage.setText("Reservation was not found; it may already be cancelled.");}catch(Exception ex){cancellationMessage.setText("Cancellation failed: "+ex.getMessage());}});
        bookingDetails.revalidate();bookingDetails.repaint();
    }
    private void addInfo(JPanel p,String title,String value){JPanel item=new JPanel();item.setLayout(new BoxLayout(item,BoxLayout.Y_AXIS));item.setBackground(Color.WHITE);item.add(fieldLabel(title.toUpperCase()));item.add(Box.createVerticalStrut(5));item.add(label(value,INK,15,true));p.add(item);}

    private static JLabel label(String text,Color color,int size,boolean bold){JLabel l=new JLabel(text);l.setForeground(color);l.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));return l;}
    private static JLabel fieldLabel(String text){return label(text,new Color(104,117,141),11,true);}
    private static JTextField field(String placeholder){JTextField f=new JTextField();f.setFont(new Font("Segoe UI",Font.PLAIN,14));f.setPreferredSize(new Dimension(230,42));f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(218,226,240),1,true),new EmptyBorder(8,11,8,11)));f.setToolTipText(placeholder);f.addFocusListener(new FocusAdapter(){@Override public void focusGained(FocusEvent e){f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BLUE,1,true),new EmptyBorder(8,11,8,11)));}@Override public void focusLost(FocusEvent e){f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(218,226,240),1,true),new EmptyBorder(8,11,8,11)));}});return f;}
    private static JPasswordField passwordField(String placeholder){JPasswordField f=new JPasswordField();f.setFont(new Font("Segoe UI",Font.PLAIN,14));f.setPreferredSize(new Dimension(230,42));f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(218,226,240),1,true),new EmptyBorder(8,11,8,11)));f.setToolTipText(placeholder);return f;}
    private static void styleInput(JComponent c){c.setFont(new Font("Segoe UI",Font.PLAIN,14));c.setPreferredSize(new Dimension(230,42));c.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(218,226,240),1,true),new EmptyBorder(7,10,7,10)));}
    private static void styleCombo(JComboBox<?> c){c.setFont(new Font("Segoe UI",Font.PLAIN,14));c.setPreferredSize(new Dimension(230,42));c.setBackground(Color.WHITE);}
    private static JButton button(String text,Color color){JButton b=new JButton(text);b.setFont(new Font("Segoe UI",Font.BOLD,14));b.setForeground(Color.WHITE);b.setBackground(color);b.setFocusPainted(false);b.setBorder(new EmptyBorder(12,17,12,17));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;}
    private static JButton textButton(String text){JButton b=new JButton(text);b.setFont(new Font("Segoe UI",Font.BOLD,13));b.setForeground(BLUE);b.setFocusPainted(false);b.setBorderPainted(false);b.setContentAreaFilled(false);return b;}
    private static void message(JPanel form,String text,boolean error){JOptionPane.showMessageDialog(form,text,error?"Sign-in error":"Notice",error?JOptionPane.ERROR_MESSAGE:JOptionPane.INFORMATION_MESSAGE);}
    @FunctionalInterface private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {void changed();default void insertUpdate(javax.swing.event.DocumentEvent e){changed();}default void removeUpdate(javax.swing.event.DocumentEvent e){changed();}default void changedUpdate(javax.swing.event.DocumentEvent e){changed();}}
}
