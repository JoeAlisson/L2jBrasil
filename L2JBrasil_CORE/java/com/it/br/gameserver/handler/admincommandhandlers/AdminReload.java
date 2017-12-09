/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.it.br.gameserver.handler.admincommandhandlers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.it.br.Config;
import com.it.br.configuration.Configurator;
import com.it.br.configuration.settings.CommandSettings;
import com.it.br.configuration.settings.EventSettings;
import com.it.br.configuration.settings.L2JBrasilSettings;
import com.it.br.configuration.settings.L2JModsSettings;
import com.it.br.configuration.settings.NetworkSettings;
import com.it.br.configuration.settings.OlympiadSettings;
import com.it.br.configuration.settings.SepulchersSettings;
import com.it.br.configuration.settings.ServerSettings;
import com.it.br.configuration.settings.SevensignsSettings;
import com.it.br.gameserver.cache.HtmCache;
import com.it.br.gameserver.datatables.DbManager;
import com.it.br.gameserver.datatables.sql.ItemTable;
import com.it.br.gameserver.datatables.sql.SkillTable;
import com.it.br.gameserver.datatables.xml.NpcTable;
import com.it.br.gameserver.datatables.xml.NpcWalkerRoutesTable;
import com.it.br.gameserver.datatables.xml.TeleportLocationTable;
import com.it.br.gameserver.handler.IAdminCommandHandler;
import com.it.br.gameserver.instancemanager.Manager;
import com.it.br.gameserver.instancemanager.QuestManager;
import com.it.br.gameserver.model.L2Multisell;
import com.it.br.gameserver.model.actor.instance.L2PcInstance;
import com.it.br.gameserver.script.faenor.FaenorScriptEngine;
import com.it.br.gameserver.scripting.CompiledScriptCache;
import com.it.br.gameserver.scripting.L2ScriptEngineManager;

/**
 * @author Guma
 */
public class AdminReload implements IAdminCommandHandler
{
    private static Map<String, Integer> admin = new HashMap<>();

    private boolean checkPermission(String command, L2PcInstance activeChar)
    {
        if (!Config.ALT_PRIVILEGES_ADMIN)
            if (!(checkLevel(command, activeChar.getAccessLevel()) && activeChar.isGM()))
            {
                activeChar.sendMessage("E necessario ter Access Level " + admin.get(command) + " para usar o comando : " + command);
                return true;
            }
        return false;
    }

    private boolean checkLevel(String command, int level)
    {
        Integer requiredAcess = admin.get(command);
        return (level >= requiredAcess);
    }

    public AdminReload()
    {
        admin.put("admin_reload", Config.admin_reload);
    }

    public Set<String> getAdminCommandList()
    {
        return admin.keySet();
    }

    public boolean useAdminCommand(String command, L2PcInstance activeChar)
    {
        StringTokenizer st = new StringTokenizer(command);
        String commandName = st.nextToken();

        if(checkPermission(commandName, activeChar)) return false;

		if (command.startsWith("admin_reload"))
		{
			sendReloadPage(activeChar);
			try
			{
				String type = st.nextToken();
				if (type.equals("multisell"))
				{
					L2Multisell.getInstance().reload();
					sendReloadPage(activeChar);
					activeChar.sendMessage("Multisell reloaded.");
				}
				else if (type.startsWith("teleport"))
				{
					TeleportLocationTable.getInstance().reloadAll();
					sendReloadPage(activeChar);
					activeChar.sendMessage("Teleport location table reloaded.");
				}
				else if (type.startsWith("skill"))
				{
					SkillTable.getInstance().reload();
					sendReloadPage(activeChar);
					activeChar.sendMessage("Skills reloaded.");
				}
				else if (type.equals("npc"))
				{
					NpcTable.getInstance().reloadAllNpc();
					sendReloadPage(activeChar);
					activeChar.sendMessage("Npcs reloaded.");
				}
				else if (type.startsWith("htm"))
				{
					HtmCache.getInstance().reload();
					sendReloadPage(activeChar);
					activeChar.sendMessage("Cache[HTML]: " + HtmCache.getInstance().getMemoryUsage() + " megabytes on " + HtmCache.getInstance().getLoadedFiles() + " files loaded");
				}
				else if (type.startsWith("item"))
				{
					ItemTable.getInstance().reload();
					sendReloadPage(activeChar);
					activeChar.sendMessage("Item templates reloaded");
				}
				else if (type.startsWith("instancemanager"))
				{
					Manager.reloadAll();
					sendReloadPage(activeChar);
					activeChar.sendMessage("All instance manager has been reloaded");
				}
				else if (type.startsWith("npcwalkers"))
				{
					NpcWalkerRoutesTable.getInstance().load();
					sendReloadPage(activeChar);
					activeChar.sendMessage("All NPC walker routes have been reloaded");
				}
				else if (type.startsWith("npcbuffers"))
				{
					DbManager.reloadAll();
					sendReloadPage(activeChar);
					activeChar.sendMessage("All Buffer skills tables have been reloaded");
				}
				else if (type.equals("configs"))
				{
					sendReloadPageConfig(activeChar);
				}
				else if (type.startsWith("admin")) 
                { 
					Config.loadGMAcessConfig();
					sendReloadPage(activeChar);
                    activeChar.sendMessage("Admin config settings reloaded"); 
                }
				else if (type.startsWith("custom")) 
                { 
					Configurator.reloadSettings(CommandSettings.class);
					Configurator.reloadSettings(L2JBrasilSettings.class);
					Configurator.reloadSettings(L2JModsSettings.class);
					sendReloadPage(activeChar);
                    activeChar.sendMessage("Custom config settings reloaded"); 
                }
				else if (type.startsWith("event")) 
                { 
					Configurator.reloadSettings(SepulchersSettings.class);
					Configurator.reloadSettings(OlympiadSettings.class);
					Configurator.reloadSettings(SevensignsSettings.class);
					Configurator.reloadSettings(EventSettings.class);
					sendReloadPage(activeChar);
                    activeChar.sendMessage("Event config settings reloaded"); 
                }
				else if (type.startsWith("main")) 
                { 
					Config.loadAltSettingsConfig();
					Config.loadBossConfig();
					Config.loadClanConfig();
					Config.loadClassConfig();
					Config.loadEnchantConfig();
					Config.loadExtensionsConfig();
					Config.loadOptionConfig();
					Config.loadOtherConfig();
					Config.loadPvPConfig();
					Config.loadRatesConfig();
					sendReloadPage(activeChar);
                    activeChar.sendMessage("Main config settings reloaded"); 
                }
				else if (type.startsWith("network")) 
                { 
					Configurator.getInstance().reloadSettings(NetworkSettings.class);
					sendReloadPage(activeChar);
                    activeChar.sendMessage("Network config settings reloaded"); 
                }
				else if (type.startsWith("security")) 
                { 
					Config.loadFloodConfig();
					Config.loadIdFactoryConfig();
					Config.loadScriptingConfig();
					sendReloadPage(activeChar);
                    activeChar.sendMessage("Security config settings reloaded"); 
                }
                else if (type.startsWith("allconfigs"))
                {
                    reloadAllConfigs();
                    activeChar.sendMessage("Network config settings reloaded");
                }
                else if (type.equals("dbs"))
				{
					DbManager.reloadAll();
					sendReloadPage(activeChar);
					activeChar.sendMessage("ItemTable reloaded.");
					activeChar.sendMessage("SkillTable reloaded.");
					activeChar.sendMessage("BufferSkillsTable reloaded.");
					activeChar.sendMessage("NpcBufferSkillIdsTable reloaded.");
					activeChar.sendMessage("GmListTable reloaded.");
					activeChar.sendMessage("ClanTable reloaded.");
					activeChar.sendMessage("AugmentationData reloaded.");
					activeChar.sendMessage("HelperBuffTable reloaded.");
				}
				else if (type.startsWith("scripts"))
                {
					try {
						ServerSettings serverSettings = Configurator.getSettings(ServerSettings.class);
						File scripts = new File(serverSettings.getDatapackDirectory() + "/data/jscript/scripts.cfg");
						if (!Config.ALT_DEV_NO_QUESTS) {
							L2ScriptEngineManager.getInstance().executeScriptList(scripts);
						}
					}
					catch (IOException ioe)
					{
						activeChar.sendMessage("Failed loading scripts.cfg, no script going to be loaded");
						ioe.printStackTrace();
					}
					try
					{
						CompiledScriptCache compiledScriptCache = L2ScriptEngineManager.getInstance().getCompiledScriptCache();
						if (compiledScriptCache == null)
						{
							activeChar.sendMessage("Compiled Scripts Cache is disabled.");
						}
						else
						{
							compiledScriptCache.purge();
							if (compiledScriptCache.isModified())
							{
								compiledScriptCache.save();
								activeChar.sendMessage("Compiled Scripts Cache was saved.");
							}
							else
							{
								activeChar.sendMessage("Compiled Scripts Cache is up-to-date.");
							}
						}
					}
					catch (IOException e)
					{
						activeChar.sendMessage( "Failed to store Compiled Scripts Cache.");
						e.printStackTrace();
					}
					QuestManager.getInstance().reloadAllQuests();
					QuestManager.getInstance().report();
					FaenorScriptEngine.getInstance().reloadPackages();
                }

			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage:  //reload <type>");
			}
		}
		return true;
	}


    private void reloadAllConfigs() {
    	Configurator.reloadAll();
        Config.loadGMAcessConfig();
        Config.loadAltSettingsConfig();
        Config.loadBossConfig();
        Config.loadClanConfig();
        Config.loadClassConfig();
        Config.loadEnchantConfig();
        Config.loadExtensionsConfig();
        Config.loadOptionConfig();
        Config.loadOtherConfig();
        Config.loadPvPConfig();
        Config.loadRatesConfig();
        Config.loadFloodConfig();
        Config.loadIdFactoryConfig();
        Config.loadScriptingConfig();
    }

    /**
	 * send reload page
	 *
	 * @param activeChar admin
	 */
	private static void sendReloadPage(L2PcInstance activeChar)
	{
		AdminHelpPage.showHelpPage(activeChar, "reload_menu.htm");
	}
	private void sendReloadPageConfig(L2PcInstance activeChar)
	{
		AdminHelpPage.showHelpPage(activeChar, "reload_menu1.htm");
	}
}
