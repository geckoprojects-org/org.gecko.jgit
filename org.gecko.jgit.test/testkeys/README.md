# Test SSH keys

Throwaway key pairs used **only** by `GitSshTransportTest`. They authenticate to an
in-process Apache MINA sshd server that is started, used, and torn down within a single
test run — they never touch any real host or account, so committing them is safe.

Generated with `ssh-keygen`:

| File               | Type    | Private-key format                     | Passphrase | Proves                                              |
|--------------------|---------|----------------------------------------|------------|-----------------------------------------------------|
| `id_ed25519`       | ed25519 | OpenSSH (`BEGIN OPENSSH PRIVATE KEY`)  | –          | ed25519 support (JSch could not parse this at all)  |
| `id_rsa_openssh`   | RSA 2048| OpenSSH (`BEGIN OPENSSH PRIVATE KEY`)  | –          | OpenSSH-format RSA (JSch 0.1.55 could not parse it)  |
| `id_rsa_pem`       | RSA 2048| classic PEM (`BEGIN RSA PRIVATE KEY`)  | –          | legacy PEM still works (no regression vs. JSch)     |
| `id_ed25519_pw`    | ed25519 | OpenSSH (`BEGIN OPENSSH PRIVATE KEY`)  | `s3cret`   | encrypted key + configured passphrase path          |

Do **not** reuse these anywhere real.
