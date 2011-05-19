/* ==========================================================================
   Copyright 2011 JaanusSiim

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   ========================================================================== */
package com.jaanussiim.android.cellid;

import android.content.Context;
import android.os.Looper;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import com.google.gson.Gson;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class Reporter extends HttpServlet {
  private static int VALUE_UNKNOWN = -1;
  private StateListener stateListener;
  private ServletConfig config;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    this.config = config;
    Thread t = new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        Context ctx = (Context)Reporter.this.config.getServletContext().getAttribute("org.mortbay.ijetty.context");
        stateListener = new StateListener(ctx);
        Looper.loop();
      }
    };
    t.start();
  }

  @Override
  public void destroy() {
    super.destroy();
    stateListener.destroy();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    System.out.println("doGet");

    if (stateListener == null) {
      PrintWriter writer = resp.getWriter();
      writer.println("{\"status\"=\"invalid\"}");
      return;
    }

    CellInfo cellInfo = stateListener.getCurrentCellInfo();
    PrintWriter writer = resp.getWriter();
    Gson gson = new Gson();
    writer.println(gson.toJson(cellInfo));
    writer.flush();
  }

  static class StateListener extends PhoneStateListener {
    private TelephonyManager telephonyManager;
    private int cellId;
    private int locationAreaCode;
    private int mobileCountryCode;
    private int mobileNetworkCode;

    public StateListener(Context ctx) {
      telephonyManager = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
      telephonyManager.listen(this, LISTEN_CELL_LOCATION | LISTEN_SERVICE_STATE);
    }

    @Override
    public void onCellLocationChanged(CellLocation location) {
      System.out.println("onCellLocationChanged: " + location);
      if (!(location instanceof GsmCellLocation)) {
        System.out.println("WTF > loc not gsm loc");
      }
      cellId = ((GsmCellLocation) location).getCid();
      locationAreaCode = ((GsmCellLocation) location).getLac();
      System.out.println(getCurrentCellInfo().toString());
    }

    public void onServiceStateChanged(final ServiceState serviceState) {
      System.out.println("onServiceStateChanged > " + serviceState);
      final String operatorNumeric = serviceState.getOperatorNumeric();
      mobileCountryCode = operatorNumeric == null ? VALUE_UNKNOWN : Integer.parseInt(operatorNumeric
          .substring(0, 3));
      mobileNetworkCode = operatorNumeric == null ? VALUE_UNKNOWN : Integer.parseInt(operatorNumeric
          .substring(3));
      System.out.println(getCurrentCellInfo().toString());
    }


    public CellInfo getCurrentCellInfo() {
      return new CellInfo(cellId, locationAreaCode, mobileCountryCode, mobileNetworkCode);
    }

    public void destroy() {
      telephonyManager.listen(this, LISTEN_NONE);
    }
  }

  static class CellInfo {
    private final int cellId;
    private final int lac;
    private final int mcc;
    private final int mnc;
    private final String status;

    public CellInfo(final int cellId, final int locationAreaCode, final int mobileCountryCode,
                    final int mobileNetworkCode) {
      this.cellId = cellId;
      this.lac = locationAreaCode;
      this.mcc = mobileCountryCode;
      this.mnc = mobileNetworkCode;
      status = getStatus();
    }

    public int getCellId() {
      return cellId;
    }

    public int getLAC() {
      return lac;
    }

    public int getMCC() {
      return mcc;
    }

    public int getMNC() {
      return mnc;
    }

    public String getStatus() {
      return validForMeasuring() ? "valid" : "invalid";
    }

    public String toString() {
      return new StringBuffer("cellid = ").append(cellId).append(" : mnc = ").append(
          mnc).append(" : mcc = ").append(mcc).append(" : lac = ")
          .append(lac).toString();
    }

    public boolean validForMeasuring() {
      return cellId != VALUE_UNKNOWN
          && mnc != VALUE_UNKNOWN
          && mcc != VALUE_UNKNOWN
          && lac != VALUE_UNKNOWN;
    }
  }
}
