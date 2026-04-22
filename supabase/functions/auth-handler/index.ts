import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(JSON.stringify({ error: "Missing Server Config" }), { 
        status: 500, 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
      })
    }

    const supabaseClient = createClient(supabaseUrl, supabaseServiceKey)
    const body = await req.json().catch(() => ({}))
    const { action, email, password, otp } = body

    if (!action) {
      return new Response(JSON.stringify({ error: 'Action is required' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    if (action === 'signup') {
      console.log(`Starting native Supabase signup for: ${email}`)
      
      // Use standard Supabase Auth signup which sends verification email automatically 
      // when SMTP is configured in the Supabase Dashboard.
      const { data: authData, error: authError } = await supabaseClient.auth.signUp({
        email,
        password,
        options: {
          data: {
            is_custom_otp: true // Metadata for later tracking if needed
          }
        }
      })

      if (authError) {
        console.error("Auth Signup Error:", authError)
        return new Response(JSON.stringify({ error: authError.message }), { 
          status: 400, 
          headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
        })
      }

      return new Response(JSON.stringify({ 
        message: 'Signup successful. Verification email sent via Supabase.', 
        success: true 
      }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    if (action === 'verify') {
      if (!email || !otp) {
        return new Response(JSON.stringify({ error: 'Email and OTP required' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      console.log(`Verifying native OTP for: ${email}`)
      const { data: verifyData, error: verifyError } = await supabaseClient.auth.verifyOtp({
        email,
        token: otp,
        type: 'signup' // or 'recovery' if resetting password
      })

      if (verifyError) {
          console.error("Verification Error:", verifyError)
          return new Response(JSON.stringify({ error: verifyError.message }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const userId = verifyData.user?.id
      if (!userId) throw new Error("Could not determine User ID")

      // Create profile and public user record
      console.log(`Creating profile and public user for: ${userId}`)
      
      await supabaseClient.from('users_profile').upsert({
          id: userId,
          email: email,
          xp: 0,
          level: 'Beginner'
      })

      await supabaseClient.from('users').upsert({
        email,
        is_verified: true
      }, { onConflict: 'email' })

      return new Response(JSON.stringify({ message: 'Account verified successfully', success: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    if (action === 'forgot-password') {
      if (!email) {
        return new Response(JSON.stringify({ error: 'Email required' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      console.log(`Requesting native password reset for: ${email}`)
      const { error: resetError } = await supabaseClient.auth.resetPasswordForEmail(email)
      
      if (resetError) {
        console.error("Reset Error:", resetError)
        return new Response(JSON.stringify({ error: resetError.message }), { 
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
        })
      }

      return new Response(JSON.stringify({ message: 'Reset email sent', success: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    if (action === 'reset-password') {
      if (!email || !otp || !password) {
        return new Response(JSON.stringify({ error: 'Email, OTP, and new password required' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      console.log(`Verifying recovery OTP for: ${email}`)
      const { data: verifyData, error: verifyError } = await supabaseClient.auth.verifyOtp({
        email,
        token: otp,
        type: 'recovery'
      })

      if (verifyError) {
        return new Response(JSON.stringify({ error: verifyError.message }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const user = verifyData.user
      if (!user) throw new Error('User not found after verification')

      console.log(`Updating password for: ${user.id}`)
      const { error: updateError } = await supabaseClient.auth.admin.updateUserById(user.id, {
        password: password
      })

      if (updateError) throw updateError

      return new Response(JSON.stringify({ message: 'Password reset successfully', success: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    return new Response(JSON.stringify({ error: 'Invalid action' }), {
      status: 400,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (err) {
    console.error("Function Error:", err)
    return new Response(JSON.stringify({ error: err.message || "Internal Server Error" }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
