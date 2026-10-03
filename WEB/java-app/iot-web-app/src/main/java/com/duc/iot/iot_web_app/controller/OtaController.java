package com.duc.iot.iot_web_app.controller;

import com.duc.iot.iot_web_app.model.Device;
import com.duc.iot.iot_web_app.model.FirmwareVersion;
import com.duc.iot.iot_web_app.model.OtaHistory;
import com.duc.iot.iot_web_app.repository.DeviceRepository;
import com.duc.iot.iot_web_app.repository.FirmwareVersionRepository;
import com.duc.iot.iot_web_app.repository.OtaHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/api/ota")
public class OtaController {

    @Autowired private FirmwareVersionRepository firmwareRepository;
    @Autowired private OtaHistoryRepository otaHistoryRepository;
    @Autowired private DeviceRepository deviceRepository;

    // Lấy danh sách firmware (API JSON)
    @GetMapping("/versions")
    @ResponseBody
    public List<FirmwareVersion> getAllVersions() {
        return firmwareRepository.findAll();
    }

    // Upload firmware mới từ trang Settings
    @PostMapping("/versions")
    public String addVersion(@RequestParam("version") String version,
                             @RequestParam("file") MultipartFile file,
                             RedirectAttributes redirectAttributes) {

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "File is empty!");
            return "redirect:/settings";
        }

        try {
            // 1. Tạo thư mục
            Path uploadPath = Paths.get("uploads/firmware");
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

            // 2. Lưu file
            String fileName = version + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 3. SHA-256 checksum
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(filePath));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }

            // 4. Lưu vào DB
            FirmwareVersion fv = new FirmwareVersion();
            fv.setVersion(version);
            fv.setFirmwareUrl("/firmware/" + fileName);
            fv.setChecksumSha256(hex.toString());
            firmwareRepository.save(fv);

            redirectAttributes.addFlashAttribute("success", "Firmware " + version + " uploaded successfully!");

        } catch (IOException | NoSuchAlgorithmException e) {
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
        }

        return "redirect:/settings";
    }

    // Kích hoạt OTA cho thiết bị - redirect về trang devices sau khi xong
    @PostMapping("/trigger/{deviceId}")
    public String triggerOta(@PathVariable Long deviceId,
                             @RequestParam(required = false) Long firmwareId,
                             RedirectAttributes redirectAttributes) {

        if (firmwareId == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn firmware trước khi OTA!");
            return "redirect:/devices";
        }

        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        Optional<FirmwareVersion> fwOpt = firmwareRepository.findById(firmwareId);

        if (deviceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Device not found: " + deviceId);
            return "redirect:/devices";
        }
        if (fwOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Firmware not found: " + firmwareId);
            return "redirect:/devices";
        }

        OtaHistory history = new OtaHistory();
        history.setDevice(deviceOpt.get());
        history.setFirmware(fwOpt.get());
        history.setStatus(OtaHistory.OtaStatus.PENDING);
        otaHistoryRepository.save(history);

        redirectAttributes.addFlashAttribute("success",
            "OTA triggered for " + deviceOpt.get().getDeviceName() + " with " + fwOpt.get().getVersion());
        return "redirect:/devices";
    }

    // Lịch sử OTA (API JSON)
    @GetMapping("/history/{deviceId}")
    @ResponseBody
    public List<OtaHistory> getHistory(@PathVariable Long deviceId) {
        return otaHistoryRepository.findByDeviceIdOrderByStartedAtDesc(deviceId);
    }
}
