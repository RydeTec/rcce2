Type Strings
    Method Encrypt$(S$, Reverse = -1)

        O$ = ""
        For i = 1 To Len(S$)
            O$ = Chr$(Asc(Mid$(S$, i, 1)) + (26 * Reverse)) + O$
        Next
        Return O$

    End Method
End Type