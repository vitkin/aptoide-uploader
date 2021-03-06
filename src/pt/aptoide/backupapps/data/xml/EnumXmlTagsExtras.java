/**
 * EnumXmlTagsExtras,	auxiliary class to Aptoide's ServiceData
 * Copyright (C) 2011  Duarte Silveira
 * duarte.silveira@caixamagica.pt
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package pt.aptoide.backupapps.data.xml;

/**
 * EnumXmlTagsExtras, typeSafes extras XML tags
 * 
 * @author dsilveira
 * @since 3.0
 *
 */
public enum EnumXmlTagsExtras {
	apklst,
	pkg,
	apphashid,
	cmt,
	screen,
	unrecognized;
	
	public static EnumXmlTagsExtras safeValueOf(String name){
		EnumXmlTagsExtras tag;
		try {
			tag = EnumXmlTagsExtras.valueOf(name);
		} catch (Exception e1) {
			tag = EnumXmlTagsExtras.unrecognized;
		}
		return tag;
	}
	
	public static EnumXmlTagsExtras reverseOrdinal(int ordinal){
		return values()[ordinal];
	}
}
