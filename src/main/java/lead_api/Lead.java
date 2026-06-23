package lead_api;

public class Lead {

    private String name;
    private String email;
    private String company;

    // Getters and setters (Spring needs these to convert JSON <-> Java object)

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }
}