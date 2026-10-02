import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class FallbackPatcher {
    public static void main(String[] args) throws Exception {
        String ctrlFile = "java-app/iot-web-app/src/main/java/com/duc/iot/iot_web_app/controller/IotController.java";
        String content = new String(Files.readAllBytes(Paths.get(ctrlFile)), StandardCharsets.UTF_8);
        
        String target = "String soil1 = vals.containsKey(\"Độ ẩm đất 1\") ? String.valueOf(vals.get(\"Độ ẩm đất 1\")) : \"\";";
        String replacement = "String soil1 = vals.containsKey(\"Độ ẩm đất 1\") ? String.valueOf(vals.get(\"Độ ẩm đất 1\")) : (vals.containsKey(\"Độ ẩm đất\") ? String.valueOf(vals.get(\"Độ ẩm đất\")) : \"\");";
        
        content = content.replace(target, replacement);
        Files.write(Paths.get(ctrlFile), content.getBytes(StandardCharsets.UTF_8));
    }
}