package vn.edu.taskmanager.auth;

public final class ProfileModels {
    private ProfileModels() { }

    public static final class Profile {
        public int userId;
        public String fullName;
        public String email;
        public String phoneNumber;
        public String birthDate;
        public String gender;
        public String avatarUrl;
    }

    public static final class UpdateRequest {
        public String fullName;
        public String email;
        public String phoneNumber;
        public String birthDate;
        public String gender;

        public UpdateRequest(String fullName, String email, String phoneNumber, String birthDate, String gender) {
            this.fullName = fullName;
            this.email = email;
            this.phoneNumber = phoneNumber;
            this.birthDate = birthDate;
            this.gender = gender;
        }
    }
}
