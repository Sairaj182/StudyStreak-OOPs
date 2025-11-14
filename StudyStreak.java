import java.util.*;


class DuplicateEntryException extends Exception {
    public DuplicateEntryException(String message) {
        super(message);
    }
    //when user tries to log streak twice a day
}
class LateEntryException extends Exception {
    public LateEntryException(String message){
        super(message);
    }
    //when user tries to log late, i.e window closed of the day
}
class StreakBrokenException extends Exception {
    public StreakBrokenException(String message) {
        super(message);
    }
    //streak breaks when someone fails to meet the targets
}

abstract class StudyTarget {
    //will be containing the target description
}

class HourTarget extends StudyTarget {
    //will be containing target hours
}

class ConceptTarget extends StudyTarget {
    //will be containing target concepts
}

class MixedTarget extends StudyTarget {
    //target hours + target concepts
}


class User {
    //User management class
    /*
        FIELDS:
            username
            hasLoggedToday?
            todaysHours
            todaysConcepts

        METHODS:
            logStudy()
            resetDay()
            getHours()
            getConcepts()
    */

}

class StreakManager {
    //logic class of the app

    /*
        - manages all users
        - manages day windows
        - streak counts
        - target checking
        - logging logic
    
        FIELDS:
           - members (in the streak)
           - target
           - streakCount
           - dayStartTime
           - WINDOW_SECONDS = 60 

    */
}

public class StudyStreak {
    public static void main(String[] args) {
        
    }
}
