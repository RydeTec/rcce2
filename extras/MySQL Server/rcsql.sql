#
# Table structure for table `actorinstance`
#

CREATE TABLE `rc_actorinstance` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `account_id` int(11) unsigned NOT NULL default '0',
  `slot` tinyint(4) NOT NULL default '0',
  `isslave` tinyint(4) NOT NULL default '0',
  `actorid` smallint(6) unsigned NOT NULL default '0',
  `area` varchar(32) NOT NULL default '',
  `name` varchar(32) NOT NULL default '',
  `tag` varchar(12) NOT NULL default '',
  `teamid` int(11) NOT NULL default '0',
  `x` float NOT NULL default '0',
  `y` float NOT NULL default '0',
  `z` float NOT NULL default '0',
  `gender` tinyint(4) NOT NULL default '0',
  `xp` int(11) NOT NULL default '0',
  `level` smallint(6) unsigned NOT NULL default '0',
  `face` smallint(6) unsigned NOT NULL default '0',
  `hair` smallint(6) unsigned NOT NULL default '0',
  `beard` smallint(6) unsigned NOT NULL default '0',
  `body` smallint(6) unsigned NOT NULL default '0',
  `script` varchar(32) NOT NULL default '',
  `dscript` varchar(32) NOT NULL default '',
  `rep` smallint(6) unsigned NOT NULL default '0',
  `gold` int(11) NOT NULL default '0',
  `slaves` tinyint(4) NOT NULL default '0',
  `homefaction` tinyint(4) NOT NULL default '0',
  `xpbarlev` tinyint(4) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_accounts`
#

CREATE TABLE `rc_accounts` (
  `account_id` int(11) unsigned NOT NULL auto_increment,
  `username` varchar(32) NOT NULL default '',
  `password` varchar(32) NOT NULL default '',
  `email` varchar(32) NOT NULL default '',
  `isdm` tinyint(4) NOT NULL default '0',
  `isbanned` tinyint(4) NOT NULL default '0',
  `ignore` text NOT NULL default '',
  PRIMARY KEY  (`account_id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_actionbar`
#

CREATE TABLE `rc_actionbar` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `slot` varchar(4) NOT NULL default '',
  `dat` varchar(32) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_attributes`
#

CREATE TABLE `rc_attributes` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `aval` smallint(6) unsigned NOT NULL default '0',
  `amax` smallint(6) unsigned NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_factionratings`
#

CREATE TABLE `rc_factionratings` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `facrat` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

CREATE TABLE `rc_resistances` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `resval` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_items`
#

CREATE TABLE `rc_items` (
  `item_id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `iid` smallint(6) unsigned NOT NULL default '0',
  `iheal` smallint(6) unsigned NOT NULL default '0',
  `iamnt` smallint(6) unsigned NOT NULL default '0',
  PRIMARY KEY  (`item_id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_itemvals`
#

CREATE TABLE `rc_itemvals` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `item_id` int(11) unsigned NOT NULL default '0',
  `val` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_memspells`
#

CREATE TABLE `rc_memspells` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `mem` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_questlog`
#

CREATE TABLE `rc_questlog` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `qname` varchar(32) NOT NULL default '',
  `qstat` varchar(32) NOT NULL default '',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_scripts`
#

CREATE TABLE `rc_scripts` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `glob` varchar(32) NOT NULL default '',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;

# --------------------------------------------------------

#
# Table structure for table `rc_spells`
#

CREATE TABLE `rc_spells` (
  `id` int(11) unsigned NOT NULL auto_increment,
  `actor_id` int(11) unsigned NOT NULL default '0',
  `known` int(11) NOT NULL default '0',
  `level` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) TYPE=MyISAM;