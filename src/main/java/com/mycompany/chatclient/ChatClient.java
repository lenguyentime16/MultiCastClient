package com.mycompany.chatclient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ChatClient extends JFrame {
    private JTextArea messageArea;
    private JTextField inputField;
    private JButton sendButton, joinGroupButton, privateMsgButton;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private Map<String, GroupChatWindow> groupChatWindows = new HashMap<>(); // Lưu trữ cửa sổ chat của các nhóm

    public ChatClient() {
        setTitle("Chat Client");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Giao diện chính
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Khu vực hiển thị tin nhắn chung (toàn server)
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Khu vực nhập tin nhắn
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputField = new JTextField();
        sendButton = new JButton("Gửi");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        // Khu vực chức năng bổ sung
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        joinGroupButton = new JButton("Tham gia nhóm");
        privateMsgButton = new JButton("Tin riêng");
        actionPanel.add(joinGroupButton);
        actionPanel.add(privateMsgButton);
        mainPanel.add(actionPanel, BorderLayout.NORTH);

        add(mainPanel);

        // Sự kiện gửi tin nhắn
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        // Sự kiện tham gia nhóm
        joinGroupButton.addActionListener(e -> joinGroup());

        // Sự kiện gửi tin riêng
        privateMsgButton.addActionListener(e -> sendPrivateMessage());

        // Đăng nhập khi khởi động
        SwingUtilities.invokeLater(this::login);
    }

    private void login() {
        username = JOptionPane.showInputDialog(this, "Nhập tên người dùng:", "Đăng nhập", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tên người dùng không hợp lệ. Đóng ứng dụng.");
            System.exit(0);
        }

        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Gửi tên người dùng đến server
            String serverPrompt = in.readLine();
            if (serverPrompt != null && serverPrompt.contains("Enter your username:")) {
                out.println(username);
            }

            // Nhận tin nhắn chào mừng
            String welcomeMsg = in.readLine();
            if (welcomeMsg != null) {
                appendMessage(welcomeMsg);
            }

            // Bắt đầu luồng nhận tin nhắn
            new Thread(new IncomingReader()).start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối đến server.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            out.println("MSG:" + message);
            inputField.setText("");
        }
    }

    private void joinGroup() {
        String groupName = JOptionPane.showInputDialog(this, "Nhập tên nhóm để tham gia:", "Tham gia nhóm", JOptionPane.PLAIN_MESSAGE);
        if (groupName != null && !groupName.trim().isEmpty()) {
            out.println("JOIN:" + groupName.trim());

            // Mở cửa sổ chat nhóm
            if (!groupChatWindows.containsKey(groupName)) {
                GroupChatWindow groupWindow = new GroupChatWindow(groupName);
                groupChatWindows.put(groupName, groupWindow);
                groupWindow.setVisible(true);
            }
        }
    }

    private void sendPrivateMessage() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField userField = new JTextField();
        JTextField msgField = new JTextField();
        panel.add(new JLabel("Người nhận:"));
        panel.add(userField);
        panel.add(new JLabel("Tin nhắn:"));
        panel.add(msgField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Gửi tin nhắn riêng tư", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String targetUser = userField.getText().trim();
            String privateMsg = msgField.getText().trim();
            if (!targetUser.isEmpty() && !privateMsg.isEmpty()) {
                out.println("PRIVATE:" + targetUser + ":" + privateMsg);
            }
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> messageArea.append(message + "\n"));
    }

    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            String message;
            try {
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("[Nhóm ")) {
                        // Tin nhắn nhóm
                        int groupNameStart = message.indexOf("[Nhóm ") + 6;
                        int groupNameEnd = message.indexOf("]", groupNameStart);
                        String groupName = message.substring(groupNameStart, groupNameEnd);
                        GroupChatWindow groupWindow = groupChatWindows.get(groupName);
                        if (groupWindow != null) {
                            groupWindow.appendMessage(message);
                        }
                    } else {
                        // Tin nhắn chung
                        appendMessage(message);
                    }
                }
            } catch (IOException ex) {
                appendMessage("Đã ngắt kết nối với server.");
            } finally {
                try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    // Lớp xử lý giao diện chat nhóm
    private class GroupChatWindow extends JFrame {
        private JTextArea groupMessageArea;
        private JTextField groupInputField;
        private JButton groupSendButton;
        private String groupName;

        public GroupChatWindow(String groupName) {
            this.groupName = groupName;
            setTitle("Chat nhóm: " + groupName);
            setSize(400, 300);
            setLocationRelativeTo(null);

            groupMessageArea = new JTextArea();
            groupMessageArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(groupMessageArea);

            groupInputField = new JTextField();
            groupSendButton = new JButton("Gửi");

            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(groupInputField, BorderLayout.CENTER);
            inputPanel.add(groupSendButton, BorderLayout.EAST);

            add(scrollPane, BorderLayout.CENTER);
            add(inputPanel, BorderLayout.SOUTH);

            groupSendButton.addActionListener(e -> sendGroupMessage());
            groupInputField.addActionListener(e -> sendGroupMessage());
        }

        private void sendGroupMessage() {
            String message = groupInputField.getText().trim();
            if (!message.isEmpty()) {
                out.println("GROUP:" + groupName + ":" + message);
                groupInputField.setText("");
            }
        }

        public void appendMessage(String message) {
            SwingUtilities.invokeLater(() -> groupMessageArea.append(message + "\n"));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            client.setVisible(true);
        });
    }
}



