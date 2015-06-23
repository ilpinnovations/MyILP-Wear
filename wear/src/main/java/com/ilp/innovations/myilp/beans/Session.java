package com.ilp.innovations.myilp.beans;

public class Session implements Comparable{

    private String sessionName;
    private String faculty;
    private String time;
    private String room;

    public Session() {
    }

    public Session(String sessionName, String faculty, String time, String room) {
        this.sessionName = sessionName;
        this.faculty = faculty;
        this.time = time;
        this.room = room;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getFaculty() {
        return faculty;
    }

    public void setFaculty(String faculty) {
        this.faculty = faculty;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    @Override
    public int compareTo(Object o) {
        char thisSlot = this.getTime().charAt(this.getTime().length()-1);
        char foreignSlot = ((Session) o).getTime().charAt(((Session) o).getTime().length()-1);
        if(thisSlot>foreignSlot)
            return 1;
        else if(thisSlot==foreignSlot)
            return 0;
        else
            return -1;
    }
}
