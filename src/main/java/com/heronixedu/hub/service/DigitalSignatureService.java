package com.heronixedu.hub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for verifying digital signatures on Windows executables.
 * Uses Windows Authenticode signature verification to ensure installers
 * are from trusted publishers.
 */
@Service
@Slf4j
public class DigitalSignatureService {

    private static final int VERIFICATION_TIMEOUT_SECONDS = 30;

    /**
     * Result of a digital signature verification.
     */
    public record SignatureVerificationResult(
            boolean isSigned,
            boolean isValid,
            boolean isTrusted,
            String signerName,
            String issuerName,
            String thumbprint,
            String timestampAuthority,
            String errorMessage,
            List<String> certificateChain
    ) {
        public static SignatureVerificationResult unsigned(String message) {
            return new SignatureVerificationResult(false, false, false, null, null, null, null, message, List.of());
        }

        public static SignatureVerificationResult invalid(String message) {
            return new SignatureVerificationResult(true, false, false, null, null, null, null, message, List.of());
        }

        public static SignatureVerificationResult valid(String signer, String issuer, String thumbprint,
                                                        String timestamp, List<String> chain, boolean trusted) {
            return new SignatureVerificationResult(true, true, trusted, signer, issuer, thumbprint, timestamp, null, chain);
        }

        public boolean isFullyVerified() {
            return isSigned && isValid && isTrusted;
        }
    }

    /**
     * Verify the digital signature of an executable file.
     * Uses PowerShell's Get-AuthenticodeSignature cmdlet for verification.
     *
     * @param filePath Path to the executable file to verify
     * @return SignatureVerificationResult containing verification details
     */
    public SignatureVerificationResult verifySignature(Path filePath) {
        if (!Files.exists(filePath)) {
            return SignatureVerificationResult.unsigned("File does not exist: " + filePath);
        }

        String extension = getFileExtension(filePath.toString()).toLowerCase();
        if (!isSignableFileType(extension)) {
            log.debug("File type {} does not support Authenticode signatures", extension);
            return SignatureVerificationResult.unsigned("File type does not support digital signatures: " + extension);
        }

        try {
            return verifyWithPowerShell(filePath);
        } catch (Exception e) {
            log.error("Failed to verify signature for {}: {}", filePath, e.getMessage());
            return SignatureVerificationResult.unsigned("Verification failed: " + e.getMessage());
        }
    }

    /**
     * Verify signature using PowerShell's Get-AuthenticodeSignature.
     */
    private SignatureVerificationResult verifyWithPowerShell(Path filePath) throws Exception {
        // Build PowerShell command to get detailed signature information
        String psScript = String.format(
                "$sig = Get-AuthenticodeSignature -FilePath '%s'; " +
                "$cert = $sig.SignerCertificate; " +
                "Write-Output \"STATUS:$($sig.Status)\"; " +
                "Write-Output \"STATUSMESSAGE:$($sig.StatusMessage)\"; " +
                "if ($cert) { " +
                "    Write-Output \"SUBJECT:$($cert.Subject)\"; " +
                "    Write-Output \"ISSUER:$($cert.Issuer)\"; " +
                "    Write-Output \"THUMBPRINT:$($cert.Thumbprint)\"; " +
                "    Write-Output \"NOTBEFORE:$($cert.NotBefore)\"; " +
                "    Write-Output \"NOTAFTER:$($cert.NotAfter)\"; " +
                "} " +
                "if ($sig.TimeStamperCertificate) { " +
                "    Write-Output \"TIMESTAMP:$($sig.TimeStamperCertificate.Subject)\"; " +
                "}",
                filePath.toAbsolutePath().toString().replace("'", "''")
        );

        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", psScript
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> output = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                log.debug("PowerShell output: {}", line);
            }
        }

        boolean completed = process.waitFor(VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return SignatureVerificationResult.unsigned("Signature verification timed out");
        }

        return parseSignatureOutput(output);
    }

    /**
     * Parse the PowerShell output into a SignatureVerificationResult.
     */
    private SignatureVerificationResult parseSignatureOutput(List<String> output) {
        String status = null;
        String statusMessage = null;
        String subject = null;
        String issuer = null;
        String thumbprint = null;
        String timestamp = null;

        for (String line : output) {
            if (line.startsWith("STATUS:")) {
                status = line.substring(7).trim();
            } else if (line.startsWith("STATUSMESSAGE:")) {
                statusMessage = line.substring(14).trim();
            } else if (line.startsWith("SUBJECT:")) {
                subject = line.substring(8).trim();
            } else if (line.startsWith("ISSUER:")) {
                issuer = line.substring(7).trim();
            } else if (line.startsWith("THUMBPRINT:")) {
                thumbprint = line.substring(11).trim();
            } else if (line.startsWith("TIMESTAMP:")) {
                timestamp = line.substring(10).trim();
            }
        }

        if (status == null) {
            return SignatureVerificationResult.unsigned("Could not retrieve signature status");
        }

        // Parse status
        boolean isSigned = !"NotSigned".equals(status);
        boolean isValid = "Valid".equals(status);
        boolean isTrusted = "Valid".equals(status);

        if (!isSigned) {
            return SignatureVerificationResult.unsigned("File is not digitally signed");
        }

        if (!isValid) {
            String errorMsg = statusMessage != null ? statusMessage : "Signature is invalid: " + status;
            return SignatureVerificationResult.invalid(errorMsg);
        }

        // Extract signer name from subject (CN= field)
        String signerName = extractCommonName(subject);

        List<String> chain = new ArrayList<>();
        if (subject != null) chain.add(subject);
        if (issuer != null && !issuer.equals(subject)) chain.add(issuer);

        return SignatureVerificationResult.valid(signerName, issuer, thumbprint, timestamp, chain, isTrusted);
    }

    /**
     * Extract the Common Name (CN) from an X.500 distinguished name.
     */
    private String extractCommonName(String distinguishedName) {
        if (distinguishedName == null) return null;

        Pattern cnPattern = Pattern.compile("CN=([^,]+)");
        Matcher matcher = cnPattern.matcher(distinguishedName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return distinguishedName;
    }

    /**
     * Check if the file type supports Authenticode signatures.
     */
    private boolean isSignableFileType(String extension) {
        return switch (extension) {
            case ".exe", ".dll", ".sys", ".msi", ".msix", ".appx", ".cab", ".cat", ".ps1", ".psm1", ".psd1" -> true;
            default -> false;
        };
    }

    /**
     * Get the file extension from a path.
     */
    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) return "";
        return path.substring(lastDot);
    }

    /**
     * Verify that the signer matches an expected publisher name.
     *
     * @param result The signature verification result
     * @param expectedPublisher The expected publisher name to match
     * @return true if the signer matches the expected publisher
     */
    public boolean verifyPublisher(SignatureVerificationResult result, String expectedPublisher) {
        if (!result.isFullyVerified() || expectedPublisher == null || expectedPublisher.isEmpty()) {
            return false;
        }

        String signerName = result.signerName();
        if (signerName == null) {
            return false;
        }

        // Case-insensitive comparison, allowing partial match
        return signerName.toLowerCase().contains(expectedPublisher.toLowerCase());
    }

    /**
     * Check if the certificate thumbprint matches a known/expected thumbprint.
     *
     * @param result The signature verification result
     * @param expectedThumbprint The expected certificate thumbprint
     * @return true if thumbprints match
     */
    public boolean verifyThumbprint(SignatureVerificationResult result, String expectedThumbprint) {
        if (!result.isFullyVerified() || expectedThumbprint == null) {
            return false;
        }

        String actualThumbprint = result.thumbprint();
        if (actualThumbprint == null) {
            return false;
        }

        return actualThumbprint.equalsIgnoreCase(expectedThumbprint.replace(" ", ""));
    }
}
