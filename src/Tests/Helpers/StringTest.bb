Strict
EnableGC

Include "Modules\Helpers\Strings.bb"

Test testEncrypt()
    Local s$ = "Hello, World!"
    Local encrypted$ = Strings::Encrypt(s$, 1)
    Assert(encrypted$ <> s$)
    Local decrypted$ = Strings::Encrypt(encrypted$, -1)
    Assert(decrypted$ = s$)
End Test
