package br.gov.detrandf.patrimonio;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

@WebServlet({"/api/patrimonio/*"})
public class PatrimonioServlet extends HttpServlet {
   private static final String DATASOURCE_JNDI = "java:comp/env/jdbc/patrimonio";

  
   private static final String SQL =
       "SELECT "
       + "  patrimonio, "
       + "  descricao, "
       + "  situacao_fisica, "
       + "  status, "
       + "  unidade, "
       + "  endereco, "
       + "  observacao "
       + "FROM patrimonio.vw_site_consulta_patrimonio "
       + "WHERE patrimonio ~ '^[0-9]+$' "
       + "  AND patrimonio::numeric = ?::numeric";

   public PatrimonioServlet() {
   }

   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String pathInfo = req.getPathInfo();
      if (pathInfo == null || pathInfo.equals("/")) {
         this.sendError(resp, 400, "Informe o número do patrimônio na URL.");
         return;
      }

      String numero = pathInfo.substring(1).trim();
      if (!numero.matches("\\d{1,15}")) {
         this.sendError(resp, 400, "Número de patrimônio inválido.");
         return;
      }

      DataSource ds;
      try {
         Context ctx = new InitialContext();
         ds = (DataSource) ctx.lookup(DATASOURCE_JNDI);
      } catch (Exception e) {
         this.log("Erro ao obter DataSource: " + e.getMessage());
         this.sendError(resp, 503, "Serviço temporariamente indisponível.");
         return;
      }

      try {
         Connection conn = ds.getConnection();
         Throwable connErr = null;

         try {
            String resultado = null;

            PreparedStatement ps = conn.prepareStatement(SQL);
            Throwable psErr = null;

            try {
               ps.setString(1, numero);
               ResultSet rs = ps.executeQuery();
               Throwable rsErr = null;

               try {
                  if (rs.next()) {
                     resultado = this.buildJson(rs);
                  }
               } catch (Throwable t) {
                  rsErr = t;
                  throw t;
               } finally {
                  if (rs != null) {
                     if (rsErr != null) {
                        try {
                           rs.close();
                        } catch (Throwable suppressed) {
                           rsErr.addSuppressed(suppressed);
                        }
                     } else {
                        rs.close();
                     }
                  }
               }
            } catch (Throwable t) {
               psErr = t;
               throw t;
            } finally {
               if (ps != null) {
                  if (psErr != null) {
                     try {
                        ps.close();
                     } catch (Throwable suppressed) {
                        psErr.addSuppressed(suppressed);
                     }
                  } else {
                     ps.close();
                  }
               }
            }

            resp.setContentType("application/json;charset=UTF-8");
            resp.setHeader("Cache-Control", "no-store");
            PrintWriter out = resp.getWriter();
            if (resultado != null) {
               resp.setStatus(200);
               out.print(resultado);
            } else {
               resp.setStatus(404);
               out.print("{\"encontrado\":false}");
            }
         } catch (Throwable t) {
            connErr = t;
            throw t;
         } finally {
            if (conn != null) {
               if (connErr != null) {
                  try {
                     conn.close();
                  } catch (Throwable suppressed) {
                     connErr.addSuppressed(suppressed);
                  }
               } else {
                  conn.close();
               }
            }
         }
      } catch (SQLException e) {
         this.log("Erro SQL: " + e.getMessage());
         this.sendError(resp, 500, "Erro ao consultar o patrimônio.");
      }
   }

   private String buildJson(ResultSet rs) throws SQLException {
      String obs = rs.getString("observacao");
      boolean temObs = obs != null && !obs.trim().isEmpty();
      StringBuilder sb = new StringBuilder();
      sb.append("{");
      sb.append("\"encontrado\":true,");
      sb.append("\"num\":").append(this.jsonStr(rs.getString("patrimonio"))).append(",");
      sb.append("\"desc\":").append(this.jsonStr(rs.getString("descricao"))).append(",");
      sb.append("\"sf\":").append(this.jsonStr(rs.getString("situacao_fisica"))).append(",");
      sb.append("\"status\":").append(this.jsonStr(rs.getString("status"))).append(",");
      sb.append("\"unidade\":").append(this.jsonStr(rs.getString("unidade"))).append(",");
      sb.append("\"end\":").append(this.jsonStr(rs.getString("endereco"))).append(",");
      if (temObs) {
         sb.append("\"obs\":{");
         sb.append("\"tipo\":\"aguardando\",");
         sb.append("\"texto\":").append(this.jsonStr(obs));
         sb.append("}");
      } else {
         sb.append("\"obs\":null");
      }

      sb.append("}");
      return sb.toString();
   }

   private String jsonStr(String value) {
      return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
   }

   private void sendError(HttpServletResponse resp, int status, String msg) throws IOException {
      resp.setStatus(status);
      resp.setContentType("application/json;charset=UTF-8");
      resp.getWriter().print("{\"erro\":\"" + msg + "\"}");
   }
}
