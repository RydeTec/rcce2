<?php

// File List?
if(isset($_GET['LIST']))
{
$i = 0;

	$DirHandle = opendir(".");

	while(false !== ($File = readdir($DirHandle)))
	{
		if($File != "." && $File != ".." && $File != "UpdateServer.php")
		{
			$Sum = md5_file($File);
			$Size = filesize($File);
			echo "$File|$Sum:$Size\n";
			//$i++; if($i == 10) die;
		}
	}
	die;
}


?>