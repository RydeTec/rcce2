<?php
// UpdateServer.php — produces a filename|md5:size listing for the rcce2
// client's update-discovery flow.
//
// Round-3 audit (security/update-channel-rce):
//
// The original version exposed `opendir(".")` to anyone with a `?LIST`
// query parameter. Any file dropped beside the update artifacts (.env,
// .htpasswd, DB backups, server-side data files placed there by mistake)
// was remotely enumerable AND downloadable via plain HTTP. Two changes:
//
//   1. Require a shared-secret token in the request (`?TOKEN=<hex>`).
//      Set RCCE_UPDATE_TOKEN in the web server environment (e.g. an
//      Apache `SetEnv` directive or a systemd service environment file).
//      Without the token, the listing returns 403.
//
//   2. Restrict enumeration to a subdirectory (defaults to `./updates/`).
//      Files outside that directory cannot be listed even with a valid
//      token. Configure via RCCE_UPDATE_DIR.
//
// The client side passes the token via the `UpdateHost$` URL — operators
// configure `Data\Game Data\Hosts.dat` to include the token in the URL.

$token = getenv('RCCE_UPDATE_TOKEN');
$updateDir = getenv('RCCE_UPDATE_DIR');
if ($updateDir === false || $updateDir === '') {
    $updateDir = __DIR__ . '/updates';
}

// File List?
if (isset($_GET['LIST'])) {
    // Token check. If RCCE_UPDATE_TOKEN is not set on the server, refuse
    // service entirely rather than fall open — explicit configuration
    // required.
    if ($token === false || $token === '') {
        http_response_code(503);
        header('Content-Type: text/plain');
        echo "Update server not configured (RCCE_UPDATE_TOKEN unset).\n";
        die;
    }
    $supplied = isset($_GET['TOKEN']) ? $_GET['TOKEN'] : '';
    if (!hash_equals($token, $supplied)) {
        http_response_code(403);
        header('Content-Type: text/plain');
        echo "Forbidden.\n";
        die;
    }

    // Resolve and validate the update directory. realpath() returns false
    // on missing paths; refuse if the configured dir doesn't exist.
    $resolved = realpath($updateDir);
    if ($resolved === false || !is_dir($resolved)) {
        http_response_code(500);
        header('Content-Type: text/plain');
        echo "Update directory missing.\n";
        die;
    }

    header('Content-Type: text/plain');
    $DirHandle = opendir($resolved);
    if ($DirHandle === false) {
        http_response_code(500);
        echo "Cannot read update directory.\n";
        die;
    }
    while (false !== ($File = readdir($DirHandle))) {
        if ($File === '.' || $File === '..' || $File === 'UpdateServer.php') {
            continue;
        }
        // Skip dotfiles entirely (.env, .git*, etc.) regardless of placement.
        if ($File[0] === '.') {
            continue;
        }
        $fullPath = $resolved . DIRECTORY_SEPARATOR . $File;
        // Defense in depth: confirm the resolved path is still inside
        // $resolved (e.g. reject symlinks pointing outside).
        $realFile = realpath($fullPath);
        if ($realFile === false || strpos($realFile, $resolved . DIRECTORY_SEPARATOR) !== 0) {
            continue;
        }
        if (!is_file($realFile)) {
            continue;
        }
        $Sum = md5_file($realFile);
        $Size = filesize($realFile);
        echo "$File|$Sum:$Size\n";
    }
    closedir($DirHandle);
    die;
}

?>
