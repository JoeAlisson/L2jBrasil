/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package com.it.br.gameserver.handler.admincommandhandlers;

import com.it.br.Config;
import com.it.br.L2DatabaseFactory;
import com.it.br.gameserver.LoginServerThread;
import com.it.br.gameserver.communitybbs.Manager.RegionBBSManager;
import com.it.br.gameserver.handler.IAdminCommandHandler;
import com.it.br.gameserver.model.GMAudit;
import com.it.br.gameserver.model.L2Object;
import com.it.br.gameserver.model.L2World;
import com.it.br.gameserver.model.actor.instance.L2PcInstance;
import com.it.br.gameserver.network.SystemMessageId;
import com.it.br.gameserver.network.serverpackets.SystemMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

/**
 * This class handles following admin commands:
 * - ban account_name = changes account access level to -100 and logs him off. If no account is specified, target's account is used.
 * - unban account_name = changes account access level to 0.
 * - jail charname [penalty_time] = jails character. Time specified in minutes. For ever if no time is specified.
 * - unjail charname = Unjails player, teleport him to Floran.
 *
 * @version $Revision: 3.0.3 $ $Date: 2017/11/09 $
 */
public class AdminBan implements IAdminCommandHandler
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

    public AdminBan()
    {
        admin.put("admin_ban", Config.admin_ban);
        admin.put("admin_unban", Config.admin_unban);
        admin.put("admin_jail", Config.admin_jail);
        admin.put("admin_unjail", Config.admin_ban);
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

		String account_name = "";
		String player = "";
		L2PcInstance plyr = null;
		if (command.startsWith("admin_ban"))
		{
			try
			{
				player = st.nextToken();
				plyr = L2World.getInstance().getPlayer(player);
			}
			catch(Exception e)
			{
				L2Object target = activeChar.getTarget();
				if (target!=null && target instanceof L2PcInstance)
					plyr = (L2PcInstance)target;
				else
					activeChar.sendMessage("Usage: //ban [account_name] (if none, target char's account gets banned)");
			}
			if (plyr != null && plyr.equals(activeChar))
				plyr.sendPacket(new SystemMessage(SystemMessageId.CANNOT_USE_ON_YOURSELF));
			else if (plyr==null)
			{
				account_name=player;
				LoginServerThread.getInstance().sendAccessLevel(account_name, -100);
				activeChar.sendMessage("Ban request sent for account "+account_name+". If you need a playername based commmand, see //ban_menu");
			}
			else
			{
				plyr.setAccountAccesslevel(-100);
				account_name = plyr.getAccountName();
				RegionBBSManager.getInstance().changeCommunityBoard();
				plyr.logout();
				activeChar.sendMessage("Account "+account_name+" banned.");
			}
		}
		else if (command.startsWith("admin_unban"))
		{
			try
			{
				account_name = st.nextToken();
				LoginServerThread.getInstance().sendAccessLevel(account_name, 0);
				activeChar.sendMessage("Unban request sent for account "+account_name+". If you need a playername based commmand, see //unban_menu");
			}
			catch(Exception e)
			{
				activeChar.sendMessage("Usage: //unban <account_name>");
				if (Config.DEBUG)
					e.printStackTrace();
			}
		}
		else if (command.startsWith("admin_jail"))
		{
			try
			{
				player = st.nextToken();
				int delay = 0;
				try
				{
					delay = Integer.parseInt(st.nextToken());
				}
				catch (NumberFormatException nfe)
				{
					activeChar.sendMessage("Usage: //jail <charname> [penalty_minutes]");
				}
				catch (NoSuchElementException nsee) {}
				L2PcInstance playerObj = L2World.getInstance().getPlayer(player);
				if (playerObj != null)
				{
					playerObj.setPunishLevel(L2PcInstance.PunishLevel.JAIL, delay);
					activeChar.sendMessage("Character "+player+" jailed for "+(delay>0 ? delay+" minutes." : "ever!"));
				}
				else
					jailOfflinePlayer(activeChar, player, delay);
			}
			catch (NoSuchElementException nsee)
			{
				activeChar.sendMessage("Usage: //jail <charname> [penalty_minutes]");
			}
			catch(Exception e)
			{
				if (Config.DEBUG)
					e.printStackTrace();
			}
		}
		else if (command.startsWith("admin_unjail"))
		{
			try
			{
				player = st.nextToken();
				L2PcInstance playerObj = L2World.getInstance().getPlayer(player);

				if (playerObj != null)
				{
					playerObj.setPunishLevel(L2PcInstance.PunishLevel.NONE, 0);
					activeChar.sendMessage("Character "+player+" removed from jail");
				}
				else
					unjailOfflinePlayer(activeChar, player);
			}
			catch (NoSuchElementException nsee)
			{
				activeChar.sendMessage("Specify a character name.");
			}
			catch(Exception e)
			{
				if (Config.DEBUG)
					e.printStackTrace();
			}
		}
		GMAudit.auditGMAction(activeChar.getName(), command, player, "");
		return true;
	}

	private void jailOfflinePlayer(L2PcInstance activeChar, String name, int delay)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();

			PreparedStatement statement = con.prepareStatement("UPDATE characters SET x=?, y=?, z=?, in_jail=?, jail_timer=? WHERE char_name=?");
			statement.setInt(1, -114356);
			statement.setInt(2, -249645);
			statement.setInt(3, -2984);
			statement.setInt(4, 1);
			statement.setLong(5, (delay > 0 ? delay * 60000L : 0));
			statement.setString(6, name);

			statement.execute();
			int count = statement.getUpdateCount();
			statement.close();

			if (count == 0)
				activeChar.sendMessage("Character not found!");
			else
				activeChar.sendMessage("Character "+name+" jailed for "+(delay>0 ? delay+" minutes." : "ever!"));
		} catch (SQLException se)
		{
			activeChar.sendMessage("SQLException while jailing player");
			if (Config.DEBUG) se.printStackTrace();
		} finally
		{
			try { con.close(); } catch (Exception e)
			{
				if (Config.DEBUG)
					e.printStackTrace();
			}
		}
	}

	private void unjailOfflinePlayer(L2PcInstance activeChar, String name)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("UPDATE characters SET x=?, y=?, z=?, in_jail=?, jail_timer=? WHERE char_name=?");
			statement.setInt(1, 17836);
			statement.setInt(2, 170178);
			statement.setInt(3, -3507);
			statement.setInt(4, 0);
			statement.setLong(5, 0);
			statement.setString(6, name);
			statement.execute();
			int count = statement.getUpdateCount();
			statement.close();
			if (count == 0)
				activeChar.sendMessage("Character not found!");
			else
				activeChar.sendMessage("Character "+name+" removed from jail");
		} catch (SQLException se)
		{
			activeChar.sendMessage("SQLException while jailing player");
			if (Config.DEBUG) se.printStackTrace();
		} finally
		{
			try
			{
				con.close();
			}
			catch (Exception e)
			{
				if (Config.DEBUG)
					e.printStackTrace();
			}
		}
	}
}
