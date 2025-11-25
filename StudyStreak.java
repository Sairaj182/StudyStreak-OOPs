import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class StudyStreak {
    private static final String USERS_FILE = "users.dat";
    private static final String GROUPS_FILE = "groups.dat";
    private static final String ACTIVITY_LOG = "activity_log.txt";
    private static final int MAX_HOURS_PER_DAY = 24;

    private static Map<String, User> users = new HashMap<>();
    private static Map<String, Group> groups = new HashMap<>();

    private static final Scanner scanner = new Scanner(System.in);
    private static LocalDate currentDate = LocalDate.now();

    public static void main(String[] args) {
        System.out.println("=== Group Study Streak System (Single-file) ===");
        System.out.println("Simulated date: " + currentDate);
        StorageManager.loadAll();

        while (true) {
            System.out.println("\n1) Login");
            System.out.println("2) Register");
            System.out.println("3) Evaluate day for all groups (simulate end of day)");
            System.out.println("4) Exit");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": loginFlow(); break;
                case "2": registerFlow(); break;
                case "3":
                    evaluateAllGroups();
                    break;
                case "4":
                    StorageManager.saveAll();
                    System.out.println("Saved data. Exiting.");
                    return;
                default: System.out.println("Invalid option"); break;
            }
        }
    }

    private static void registerFlow() {
        try {
            System.out.print("Choose username: ");
            String uname = scanner.nextLine().trim();
            if (users.containsKey(uname)) {
                throw new UserAlreadyExistsException("Username already exists.");
            }
            System.out.print("Choose password: ");
            String password = scanner.nextLine();
            String hash = SecurityUtil.hash(password);
            User u = new User(uname, hash);
            users.put(uname, u);
            ActivityLogger.logGlobal("User registered: " + uname);
            StorageManager.saveAll();
            System.out.println("User registered. Please login.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void loginFlow() {
        try {
            System.out.print("Username: ");
            String uname = scanner.nextLine().trim();
            if (!users.containsKey(uname)) throw new InvalidLoginException("User not found.");
            System.out.print("Password: ");
            String password = scanner.nextLine();
            User user = users.get(uname);
            if (!user.verifyPassword(password)) throw new InvalidLoginException("Invalid password.");
            System.out.println("Welcome, " + uname + "!");
            userMenu(user);
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
        }
    }

    private static void userMenu(User user) {
        while (true) {
            System.out.println("\n--- Main Menu (Logged in as " + user.getUsername() + ") ---");
            System.out.println("1) Create Group");
            System.out.println("2) Request to Join Group");
            System.out.println("3) Approve/Reject Requests (admin only)");
            System.out.println("4) Log study hours for a group");
            System.out.println("5) View group dashboard (leaderboard, streak)");
            System.out.println("6) View my group statuses");
            System.out.println("7) Logout");
            System.out.print("Choose: ");
            String opt = scanner.nextLine().trim();
            try {
                switch (opt) {
                    case "1": createGroupFlow(user); break;
                    case "2": requestJoinFlow(user); break;
                    case "3": manageRequestsFlow(user); break;
                    case "4": logHoursFlow(user); break;
                    case "5": viewGroupDashboardFlow(); break;
                    case "6": user.viewStatuses(); break;
                    case "7":
                        StorageManager.saveAll();
                        ActivityLogger.logGlobal("User logged out: " + user.getUsername());
                        return;
                    default: System.out.println("Invalid option"); break;
                }
            } catch (Exception e) {
                System.out.println("Operation failed: " + e.getMessage());
            }
        }
    }

    private static void createGroupFlow(User creator) throws Exception {
        System.out.print("Enter new group name: ");
        String gname = scanner.nextLine().trim();
        if (groups.containsKey(gname)) throw new Exception("Group already exists");
        System.out.print("Enter daily target hours (integer): ");
        int target = Integer.parseInt(scanner.nextLine().trim());
        Group g = new Group(gname, creator.getUsername(), target);
        groups.put(gname, g);
        g.addMember(creator.getUsername());
        creator.joinGroupDirect(gname);
        creator.setAdminForGroup(gname, true);
        ActivityLogger.log(gname, "Group created by " + creator.getUsername() + " with target hours: " + target);
        StorageManager.saveAll();
        System.out.println("Group created and you are admin.");
    }

    private static void requestJoinFlow(User user) throws Exception {
        System.out.print("Enter group name to request join: ");
        String gname = scanner.nextLine().trim();
        if (!groups.containsKey(gname)) throw new GroupNotFoundException("Group not found");
        Group g = groups.get(gname);
        g.addJoinRequest(user.getUsername());
        ActivityLogger.log(gname, "Join request: " + user.getUsername());
        StorageManager.saveAll();
        System.out.println("Join request submitted.");
    }

    private static void manageRequestsFlow(User user) throws Exception {
        System.out.print("Enter group name to manage requests: ");
        String gname = scanner.nextLine().trim();
        if (!groups.containsKey(gname)) throw new GroupNotFoundException("Group not found");
        Group g = groups.get(gname);
        if (!g.getAdminUsername().equals(user.getUsername())) throw new NotGroupAdminException("Only admin can manage requests");
        List<String> reqs = g.getJoinRequests();
        if (reqs.isEmpty()) {
            System.out.println("No pending requests.");
            return;
        }
        System.out.println("Pending requests:");
        for (int i = 0; i < reqs.size(); ++i) {
            System.out.println((i+1) + ") " + reqs.get(i));
        }
        System.out.print("Enter request number to approve (or 0 to cancel): ");
        int choice = Integer.parseInt(scanner.nextLine().trim());
        if (choice <= 0 || choice > reqs.size()) {
            System.out.println("No action taken.");
            return;
        }
        String candidate = reqs.get(choice-1);
        System.out.print("Approve (A) or Reject (R)? ");
        String ar = scanner.nextLine().trim().toUpperCase();
        if (ar.equals("A")) {
            g.approveRequest(candidate);
            if (users.containsKey(candidate)) {
                users.get(candidate).joinGroupDirect(gname);
            }
            ActivityLogger.log(gname, "Admin " + user.getUsername() + " approved " + candidate);
            System.out.println("Approved.");
        } else {
            g.rejectRequest(candidate);
            ActivityLogger.log(gname, "Admin " + user.getUsername() + " rejected " + candidate);
            System.out.println("Rejected.");
        }
        StorageManager.saveAll();
    }

    private static void logHoursFlow(User user) throws Exception {
        System.out.print("Enter group name you want to log for: ");
        String gname = scanner.nextLine().trim();
        if (!groups.containsKey(gname)) throw new GroupNotFoundException("Group not found");
        Group g = groups.get(gname);
        if (!user.isMemberOfGroup(gname)) throw new UserNotInGroupException("You are not a member of this group");
        System.out.print("Enter hours studied today (integer, max 24): ");
        int hrs = Integer.parseInt(scanner.nextLine().trim());
        if (hrs < 0 || hrs > MAX_HOURS_PER_DAY) throw new InvalidHoursException("Hours must be between 0 and " + MAX_HOURS_PER_DAY);
        user.logForGroup(gname, hrs);
        g.updateTodayStudy(user.getUsername(), hrs);
        ActivityLogger.log(gname, user.getUsername() + " logged " + hrs + " hours today.");
        StorageManager.saveAll();
        System.out.println("Logged. Current group leaderboard:");
        g.printLeaderboard();
    }

    private static void viewGroupDashboardFlow() {
        System.out.print("Enter group name to view dashboard: ");
        String gname = scanner.nextLine().trim();
        if (!groups.containsKey(gname)) {
            System.out.println("Group not found");
            return;
        }
        Group g = groups.get(gname);
        System.out.println("\n=== Dashboard for group: " + gname + " ===");
        System.out.println("Admin: " + g.getAdminUsername());
        System.out.println("Streak: " + g.getStreakCount());
        System.out.println("Target hours/day: " + g.getTargetHours());
        System.out.println("Members (" + g.getMembers().size() + "): " + String.join(", ", g.getMembers()));
        System.out.println("\nLeaderboard (today):");
        g.printLeaderboard();
        System.out.println("\nPending join requests: " + g.getJoinRequests());
    }

    private static void evaluateAllGroups() {
        System.out.println("Simulating end-of-day for " + currentDate + " and evaluating all groups...");
        for (Group g : groups.values()) {
            try {
                g.evaluateTodayAndUpdate(users);
            } catch (Exception e) {
                System.out.println("Error evaluating group " + g.getGroupName() + ": " + e.getMessage());
            }
        }
        currentDate = currentDate.plusDays(1);
        System.out.println("Advanced simulated date to " + currentDate + ". Resetting daily logs for new day...");
        for (Group g : groups.values()) {
            g.resetTodayMaps();
        }
        for (User u : users.values()) {
            u.resetDailyFlags();
        }
        StorageManager.saveAll();
        System.out.println("Evaluation complete. Activity log updated.");
    }

    static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String username;
        private final String passwordHash;
        private Map<String, Boolean> groupAdminMap = new HashMap<>();
        private Map<String, UserGroupStatus> groupStatuses = new HashMap<>();

        public User(String username, String passwordHash) {
            this.username = username;
            this.passwordHash = passwordHash;
        }

        public String getUsername() { return username; }

        public boolean verifyPassword(String plain) {
            try {
                return SecurityUtil.hash(plain).equals(passwordHash);
            } catch (Exception e) { return false; }
        }

        public void setAdminForGroup(String groupName, boolean isAdmin) {
            groupAdminMap.put(groupName, isAdmin);
        }

        public boolean isAdminForGroup(String groupName) {
            return groupAdminMap.getOrDefault(groupName, false);
        }

        public void joinGroupDirect(String groupName) {
            if (!groupStatuses.containsKey(groupName)) {
                groupStatuses.put(groupName, new UserGroupStatus(groupName));
            }
        }

        public void leaveGroup(String groupName) {
            groupAdminMap.remove(groupName);
            groupStatuses.remove(groupName);
        }

        public boolean isMemberOfGroup(String groupName) {
            return groupStatuses.containsKey(groupName);
        }

        public void logForGroup(String groupName, int hours) throws LateLogException {
            if (!groupStatuses.containsKey(groupName)) {
                throw new RuntimeException("Not a member of this group");
            }
            UserGroupStatus s = groupStatuses.get(groupName);
            if (s.getLastLogDate() != null && s.getLastLogDate().isEqual(currentDate)) {
                throw new LateLogException("You have already logged today for this group.");
            }
            s.setTodayHours(hours);
            s.setHasLoggedToday(true);
            s.setLastLogDate(currentDate);
            if (hours < groups.get(groupName).getTargetHours()) {
                s.incrementConsecutiveFailures();
            } else {
                s.clearConsecutiveFailures();
            }
        }

        public void viewStatuses() {
            if (groupStatuses.isEmpty()) {
                System.out.println("You are not in any group.");
                return;
            }
            System.out.println("Your group statuses:");
            for (UserGroupStatus s : groupStatuses.values()) {
                System.out.println("Group: " + s.getGroupName() +
                        " | LoggedToday: " + s.isHasLoggedToday() +
                        " | TodayHours: " + s.getTodayHours() +
                        " | ConsecutiveFailures: " + s.getConsecutiveFailures() +
                        " | LastLogDate: " + s.getLastLogDate());
            }
        }

        public void resetDailyFlags() {
            for (UserGroupStatus s : groupStatuses.values()) {
                if (s.getLastLogDate() == null || s.getLastLogDate().isBefore(currentDate)) {
                    s.setHasLoggedToday(false);
                    s.setTodayHours(0);
                }
            }
        }

        public Map<String, UserGroupStatus> getGroupStatuses() {
            return groupStatuses;
        }
    }

    static class UserGroupStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String groupName;
        private int todayHours = 0;
        private boolean hasLoggedToday = false;
        private int consecutiveFailures = 0;
        private LocalDate lastLogDate = null;

        public UserGroupStatus(String groupName) { this.groupName = groupName; }

        public String getGroupName() { return groupName; }
        public int getTodayHours() { return todayHours; }
        public void setTodayHours(int h) { this.todayHours = h; }
        public boolean isHasLoggedToday() { return hasLoggedToday; }
        public void setHasLoggedToday(boolean b) { this.hasLoggedToday = b; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public void incrementConsecutiveFailures() { consecutiveFailures++; }
        public void clearConsecutiveFailures() { consecutiveFailures = 0; }
        public LocalDate getLastLogDate() { return lastLogDate; }
        public void setLastLogDate(LocalDate d) { lastLogDate = d; }
    }

    static class Group implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String groupName;
        private int targetHours;
        private int streakCount = 0;
        private String adminUsername;
        private List<String> members = new ArrayList<>();
        private List<String> joinRequests = new ArrayList<>();
        private transient Map<String, Integer> todayStudyMap = new HashMap<>();
        private LocalDate lastUpdatedDate = currentDate;

        public Group(String groupName, String adminUsername, int targetHours) {
            this.groupName = groupName;
            this.adminUsername = adminUsername;
            this.targetHours = targetHours;
            this.todayStudyMap = new HashMap<>();
        }

        private void ensureTransient() {
            if (todayStudyMap == null) todayStudyMap = new HashMap<>();
        }

        public String getGroupName() { return groupName; }
        public int getTargetHours() { return targetHours; }
        public String getAdminUsername() { return adminUsername; }
        public void setTargetHours(int h) { targetHours = h; }

        public List<String> getMembers() { return members; }
        public List<String> getJoinRequests() { return joinRequests; }

        public void addMember(String username) {
            if (!members.contains(username)) members.add(username);
        }
        public void removeMember(String username) {
            members.remove(username);
        }
        public void addJoinRequest(String username) throws DuplicateJoinRequestException {
            if (joinRequests.contains(username)) throw new DuplicateJoinRequestException("Already requested.");
            if (members.contains(username)) throw new RuntimeException("Already a member");
            joinRequests.add(username);
        }
        public void approveRequest(String username) {
            joinRequests.remove(username);
            addMember(username);
        }
        public void rejectRequest(String username) {
            joinRequests.remove(username);
        }

        public int getStreakCount() { return streakCount; }

        public void updateTodayStudy(String username, int hours) {
            ensureTransient();
            todayStudyMap.put(username, hours);
            sortLeaderboard();
        }

        public void printLeaderboard() {
            ensureTransient();
            List<String> sorted = members.stream()
                    .sorted((a, b) -> Integer.compare(todayStudyMap.getOrDefault(b, 0), todayStudyMap.getOrDefault(a, 0)))
                    .collect(Collectors.toList());
            for (int i = 0; i < sorted.size(); ++i) {
                String u = sorted.get(i);
                System.out.println((i+1) + ". " + u + " - " + todayStudyMap.getOrDefault(u, 0) + " hrs");
            }
        }

        private void sortLeaderboard() {
            ensureTransient();
            members.sort((a, b) -> Integer.compare(todayStudyMap.getOrDefault(b, 0), todayStudyMap.getOrDefault(a, 0)));
        }

        public void evaluateTodayAndUpdate(Map<String, User> allUsers) {
            ensureTransient();
            int total = members.size();
            if (total == 0) return;
            int met = 0;
            List<String> toRemove = new ArrayList<>();
            for (String m : new ArrayList<>(members)) {
                User u = allUsers.get(m);
                if (u == null) {
                    toRemove.add(m);
                    continue;
                }
                UserGroupStatus s = u.getGroupStatuses().get(groupName);
                boolean metToday = false;
                if (s != null && s.isHasLoggedToday() && s.getTodayHours() >= targetHours) {
                    met++;
                    metToday = true;
                }
                if (s != null) {
                    if (!metToday) s.incrementConsecutiveFailures();
                    if (s.getConsecutiveFailures() >= 3) {
                        toRemove.add(m);
                        ActivityLogger.log(groupName, "Auto-removed user " + m + " after " + s.getConsecutiveFailures() + " consecutive failures.");
                    }
                }
            }
            int required = (int) Math.ceil(0.75 * total);
            if (met >= required) {
                streakCount++;
                ActivityLogger.log(groupName, "Streak incremented to " + streakCount + " (met " + met + "/" + total + ")");
            } else {
                streakCount = 0;
                ActivityLogger.log(groupName, "Streak broken. Met " + met + "/" + total + " (required " + required + ")");
            }
            for (String r : toRemove) {
                removeMember(r);
                User uu = allUsers.get(r);
                if (uu != null) uu.leaveGroup(groupName);
            }
            lastUpdatedDate = currentDate;
        }

        public void resetTodayMaps() {
            ensureTransient();
            todayStudyMap.clear();
        }

        private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
            ois.defaultReadObject();
            this.todayStudyMap = new HashMap<>();
        }
    }

    static class ActivityLogger {
        public static void log(String groupName, String message) {
            String line = "[" + currentDate + "] [" + groupName + "] " + message;
            appendLine(line);
        }
        public static void logGlobal(String message) {
            String line = "[" + currentDate + "] [GLOBAL] " + message;
            appendLine(line);
        }
        private static synchronized void appendLine(String line) {
            try (FileWriter fw = new FileWriter(ACTIVITY_LOG, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.println(line);
            } catch (IOException e) {
                System.out.println("Failed to write activity log: " + e.getMessage());
            }
        }
    }

    static class StorageManager {
        @SuppressWarnings("unchecked")
        public static void loadAll() {
            Path usersPath = Paths.get(USERS_FILE).toAbsolutePath();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(usersPath.toFile()))) {
                Object o = ois.readObject();
                if (o instanceof Map) {
                    users = (Map<String, User>) o;
                }
            } catch (FileNotFoundException ignored) {
                System.out.println("Users file not found: " + usersPath);
            } catch (Exception e) {
                System.out.println("Failed to load users from " + usersPath + ": " + e.getMessage());
                e.printStackTrace();
            }

            Path groupsPath = Paths.get(GROUPS_FILE).toAbsolutePath();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(groupsPath.toFile()))) {
                Object o = ois.readObject();
                if (o instanceof Map) {
                    groups = (Map<String, Group>) o;
                }
            } catch (FileNotFoundException ignored) {
                System.out.println("Groups file not found: " + groupsPath);
            } catch (Exception e) {
                System.out.println("Failed to load groups from " + groupsPath + ": " + e.getMessage());
                e.printStackTrace();
            }

            for (Group g : groups.values()) g.resetTodayMaps();

            System.out.println("Loaded " + users.size() + " users and " + groups.size() + " groups from disk.");
        }

        public static void saveAll() {
            saveObjectToFile(users, USERS_FILE, "users");
            saveObjectToFile(groups, GROUPS_FILE, "groups");
        }

        private static void saveObjectToFile(Object obj, String fileName, String label) {
            Path path = Paths.get(fileName).toAbsolutePath();
            Path tmp = Paths.get(fileName + ".tmp").toAbsolutePath();
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                oos.writeObject(obj);
            } catch (Exception e) {
                System.out.println("Failed to write " + label + " temp file " + tmp + ": " + e.getMessage());
                e.printStackTrace();
                return;
            }
            try {
                try {
                    Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException amnse) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("Saved " + label + " to " + path);
            } catch (Exception e) {
                System.out.println("Failed to move " + tmp + " to " + path + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static class SecurityUtil {
        public static String hash(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException("Hashing failed");
            }
        }
    }

    static class UserAlreadyExistsException extends Exception {
        private static final long serialVersionUID = 1L;
        public UserAlreadyExistsException(String m){ super(m); }
    }
    static class InvalidLoginException extends Exception {
        private static final long serialVersionUID = 2L;
        public InvalidLoginException(String m){ super(m); }
    }
    static class GroupNotFoundException extends Exception {
        private static final long serialVersionUID = 3L;
        public GroupNotFoundException(String m){ super(m); }
    }
    static class NotGroupAdminException extends Exception {
        private static final long serialVersionUID = 4L;
        public NotGroupAdminException(String m){ super(m); }
    }
    static class DuplicateJoinRequestException extends Exception {
        private static final long serialVersionUID = 5L;
        public DuplicateJoinRequestException(String m){ super(m); }
    }
    static class UserNotInGroupException extends Exception {
        private static final long serialVersionUID = 6L;
        public UserNotInGroupException(String m){ super(m); }
    }
    static class LateLogException extends Exception {
        private static final long serialVersionUID = 7L;
        public LateLogException(String m){ super(m); }
    }
    static class InvalidHoursException extends Exception {
        private static final long serialVersionUID = 8L;
        public InvalidHoursException(String m){ super(m); }
    }
}
