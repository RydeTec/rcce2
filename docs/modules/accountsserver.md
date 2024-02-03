<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**AccountsServer.bb**

This module contains the following globals:  

*   [Accounts.AccountsWindow](#GAccounts)

This module contains the following types:  

*   [AccountsWindow](#TAccountsWindow)
*   [Account](#TAccount)
*   [ActionBarData](#TActionBarData)

This module contains the following functions:  

*   [SetLoginStatus](#FSetLoginStatus)
*   [SetAccountDMStatus](#FSetAccountDMStatus)
*   [SetAccountBanStatus](#FSetAccountBanStatus)
*   [AddAccount](#FAddAccount)
*   [SaveAccounts](#FSaveAccounts)
*   [LoadAccounts](#FLoadAccounts)
*   [CreateAccountsWindow](#FCreateAccountsWindow)
*   [DeleteCharacter](#FDeleteCharacter)

  

* * *

  

**Accounts.AccountsWindow (global)**  
  
This global contains the object representing the server's Accounts window, and is set when the window is created.

  

* * *

  

**AccountsWindow (type)**  
  
This type represents a server Accounts window. It contains the handles of the window and all child gadgets, and counters for the total number of existing accounts.

  

**Account (type)**  
  
This type represents a player account on the server. It contains the username, password hash, email address, status, and all characters created on the account (up to a total of 10).

  

**ActionBarData (type)**  
  
This type represents a player character's current action bar settings. It contains a string for each of the 36 action bar slots, and an ID for each slot for MySQL use only.

  

* * *

  
  
  

**SetLoginStatus(A.Account, Status)**  
  
Return value: None  
  
Parameters:  

*   _A.Account_ - The account to change the login status of
*   _Status_ - The new status

  
This function sets an account's login status. The value of the Status parameter should be -1 to mean that the account is logged out, or otherwise a character ID (from 0 to 9) specifying which character has been logged in on the account. The server's Accounts window is updated to show the new status.

  
  
  

**SetAccountDMStatus(A.Account, Flag)**  
  
Return value: None  
  
Parameters:  

*   _A.Account_ - The account to change the GM status of
*   _Flag_ - A True/False flag for the new status

  
This function changes whether or not an account is flagged as GM. It calls [SetLoginStatus](#FSetLoginStatus) to update the display on the server's Accounts window. It also updates the total count of GMs to reflect the change.

  
  
  

**SetAccountBanStatus(A.Account, Flag)**  
  
Return value: None  
  
Parameters:  

*   _A.Account_ - The account to change the ban status of
*   _Flag_ - A True/False flag for the new status

  
This function changes whether or not an account is flagged as banned. It calls [SetLoginStatus](#FSetLoginStatus) to update the display on the server's Accounts window. It also updates the total count of banned accounts to reflect the change.

  
  
  

**AddAccount(User$, Pass$, Email$)**  
  
Return value: None  
  
Parameters:  

*   _User$_ - Username for the new account
*   _Pass$_ - Password hash for the new account
*   _Email$_ - Email address for the new account

  
This function creates a new Account object, sets its initial values, adds the new account to the server's Accounts window, and appends it to the Accounts.dat file.

  
  
  

**SaveAccounts()**  
  
Return value: Success flag  
  
Parameters: None  
  
This function saves all accounts and characters to the Accounts.dat file and returns True if successful.

  
  
  

**LoadAccounts()**  
  
Return value: Number of accounts loaded  
  
Parameters: None  
  
This function loads all accounts and characters from the Accounts.dat file and returns the total number loaded. It also displays all the loaded accounts on the server's Accounts window.

  
  
  

**CreateAccountsWindow.AccountsWindow()**  
  
Return value: Handle of the newly created AccountsWindow object  
  
Parameters: None  
  
This function creates a new AccountsWindow and all gadgets, then returns the handle. If the MySQL flag is set, it redirects to [My\_CreateAccountsWindow](mysql.md#FMy_CreateAccountsWindow) instead.

  
  
  

**DeleteCharacter(A.Account, Number)**  
  
Return value: None  
  
Parameters:  

*   _A.Account_ - The account to delete a character from
*   _Number_ - The number of the character to delete

  
This function permanently deletes a character from an account. The Number parameter should be in the range 0 to 9.

  
  
  

**PlayerIgnoring(A1.ActorInstance, A2.ActorInstance)**  
  
Return value: A player's position (if any) in another player's ignore list  
  
Parameters:  

*   _A1.ActorInstance_ - The player character whose ignore list we are checking
*   _A2.ActorInstance_ - The player character whose account we are searching for

  
This function checks whether a player has another player on their ignore list. All comparisons are done at the account (rather than character) level. If the player is currently ignored the position in the ignore list will be returned. Ignore lists are comma delimited strings stored in the [Account](#TAccount) type.