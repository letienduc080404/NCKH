import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDB {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/iot_db";
        String user = "postgres";
        String password = "123";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
             
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sensor_readings");
            if (rs.next()) {
                System.out.println("Total records in sensor_readings: " + rs.getLong(1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}