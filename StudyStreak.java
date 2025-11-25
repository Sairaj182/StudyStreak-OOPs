import java.util.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

class DuplicateEntryException extends Exception {
    public DuplicateEntryException(String message) {
        super(message);
    }
}

class StreakBrokenException extends Exception {
    public StreakBrokenException(String message) {
        super(message);
    }
}

class User {
    private String username;
    private boolean hasLoggedToday = false;
    private int todaysHours = 0;
    private int todaysConcepts = 0;

    public User(String username) {
        this.username = username;
    }

    public void logStudy(int hours, int concepts) 
        throws DuplicateEntryException 
    {
        if(hasLoggedToday) {
            throw new DuplicateEntryException("User " + username + " has already logged today!");
        }
        this.hasLoggedToday = true;
        this.todaysHours = hours;
        this.todaysConcepts = concepts;
    }

    public void resetDay() {
        hasLoggedToday = false;
        todaysHours = 0;
        todaysConcepts = 0;
    }

    public boolean hasLogged() { return hasLoggedToday; }
    public String getUsername() { return username; }
    public void setHasLogged(boolean value) {
        this.hasLoggedToday = value;
        if(!value) {
            this.todaysHours = 0;
            this.todaysConcepts = 0;
        }
    }
}

class StreakManager {
    private List<User> members = new ArrayList<>();
    private int streakCount = 0;
    private LocalDate lastLoggedDate;

    private final String FILE_NAME = "streak.txt";

    public StreakManager(List<User> users) {
        this.members = users;
        loadFromFile();
    }


    public void loadFromFile() {
        File file = new File(FILE_NAME);
        if(!file.exists()) {
            streakCount = 0;
            lastLoggedDate = LocalDate.now();
            return;
        }

        try(BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            if(line != null && line.contains("=")) {
                try {
                    streakCount = Integer.parseInt(line.split("=")[1]);
                } catch(NumberFormatException nfe) {
                    streakCount = 0;
                }
            } else {
                streakCount = 0;
            }

            line = br.readLine();
            if(line != null && line.contains("=")) {
                String dateStr = line.split("=")[1];
                try {
                    lastLoggedDate = LocalDate.parse(dateStr);
                } catch(DateTimeParseException dtpe) {
                    lastLoggedDate = LocalDate.now();
                }
            } else {
                lastLoggedDate = LocalDate.now();
            }

            String memberLine;
            while((memberLine = br.readLine()) != null) {
                String[] parts = memberLine.split(":");
                if(parts.length != 2) continue;
                String username = parts[0];
                boolean logged = Boolean.parseBoolean(parts[1]);
                for(User u : members) {
                    if(u.getUsername().equals(username)) {
                        u.setHasLogged(logged);
                        break;
                    }
                }
            }
        } catch(Exception e) {
            streakCount = 0;
            lastLoggedDate = LocalDate.now();
            for(User u : members) u.setHasLogged(false);
        }
    }

    public void saveToFile() {
        try(PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME))) {
            lastLoggedDate = LocalDate.now();
            pw.println("streakCount=" + streakCount);
            pw.println("lastLoggedDate=" + lastLoggedDate);

            for(User u : members) {
                pw.println(u.getUsername() + ":" + u.hasLogged());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void evaluateDay() throws StreakBrokenException {
        
        for(User u : members) {
            if(!u.hasLogged()) {
                streakCount = 0;
                saveToFile();
                throw new StreakBrokenException("Group streak broken! " + u.getUsername() + " didn't log!");
            }
        }

        streakCount++;
        saveToFile();

        for(User u : members) u.resetDay();
    }

    public int getStreakCount() { return streakCount; }
}

public class StudyStreak {
    public static void main(String[] args) {

        User u1 = new User("Ayush");
        User u2 = new User("Sairaj");
        User u3 = new User("Kalyan");

        StreakManager manager = new StreakManager(Arrays.asList(u1, u2, u3));

        try {
            u1.logStudy(2, 3);
            u2.logStudy(1, 2);
            u3.logStudy(2, 4);

            manager.saveToFile();
            manager.evaluateDay();

        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }
}